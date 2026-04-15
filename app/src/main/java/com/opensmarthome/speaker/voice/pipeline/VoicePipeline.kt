package com.opensmarthome.speaker.voice.pipeline

import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.router.ConversationRouter
import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.voice.stt.SpeechToText
import com.opensmarthome.speaker.voice.stt.SttResult
import com.opensmarthome.speaker.voice.tts.TextToSpeech
import com.opensmarthome.speaker.voice.wakeword.WakeWordDetector
import com.opensmarthome.speaker.assistant.model.AssistantSession
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class VoicePipeline(
    private val stt: SpeechToText,
    private val tts: TextToSpeech,
    private val router: ConversationRouter,
    private val toolExecutor: ToolExecutor,
    private val moshi: Moshi,
    private val wakeWordDetector: WakeWordDetector? = null,
    private val continuousMode: Boolean = false
) {
    private val _state = MutableStateFlow<VoicePipelineState>(VoicePipelineState.Idle)
    val state: StateFlow<VoicePipelineState> = _state.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _lastResponse = MutableStateFlow("")
    val lastResponse: StateFlow<String> = _lastResponse.asStateFlow()

    private var currentSession: AssistantSession? = null
    private val conversationHistory = mutableListOf<AssistantMessage>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watchdogJob: Job? = null

    companion object {
        private const val MAX_TOOL_ROUNDS = 10
        private const val WATCHDOG_TIMEOUT_MS = 5 * 60 * 1000L
    }

    fun startWakeWordListening() {
        val detector = wakeWordDetector ?: return
        _state.value = VoicePipelineState.WakeWordListening
        detector.start {
            Timber.d("Wake word detected!")
            scope.launch { startListening() }
        }
        startWatchdog()
    }

    fun stopWakeWordListening() {
        wakeWordDetector?.stop()
        cancelWatchdog()
        _state.value = VoicePipelineState.Idle
    }

    suspend fun startListening() {
        // Reset for re-entry
        if (_state.value is VoicePipelineState.Speaking) {
            tts.stop()
        }
        stt.stopListening()
        _partialText.value = ""
        _lastResponse.value = ""

        _state.value = VoicePipelineState.Listening
        resetWatchdog()

        var finalText = ""
        try {
            stt.startListening().collect { result ->
                when (result) {
                    is SttResult.Partial -> {
                        _partialText.value = result.text
                    }
                    is SttResult.Final -> {
                        finalText = result.text
                        _partialText.value = result.text
                    }
                    is SttResult.Error -> {
                        Timber.w("STT error: ${result.message}")
                        _lastResponse.value = "Could not hear you. Tap the mic to try again."
                        _state.value = VoicePipelineState.Error(result.message)
                        delay(2000)
                        _state.value = VoicePipelineState.Idle
                        return@collect
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "STT failed")
            _lastResponse.value = "Voice recognition unavailable."
            _state.value = VoicePipelineState.Error(e.message ?: "STT error")
            delay(2000)
            _state.value = VoicePipelineState.Idle
            return
        }

        if (finalText.isNotBlank()) {
            processUserInput(finalText)
        } else {
            _state.value = VoicePipelineState.Idle
        }
    }

    suspend fun processUserInput(text: String) {
        _state.value = VoicePipelineState.Processing
        _partialText.value = text
        _lastResponse.value = ""
        resetWatchdog()

        try {
            val provider = router.resolveProvider()
            if (currentSession == null) {
                currentSession = provider.startSession()
            }

            val userMessage = AssistantMessage.User(content = text)
            conversationHistory.add(userMessage)
            trimConversationHistory()

            val tools = toolExecutor.availableTools()
            var toolRounds = 0

            _state.value = VoicePipelineState.Thinking

            while (toolRounds < MAX_TOOL_ROUNDS) {
                val response = provider.send(currentSession!!, conversationHistory, tools)

                when (response) {
                    is AssistantMessage.Assistant -> {
                        conversationHistory.add(response)

                        if (response.toolCalls.isNotEmpty()) {
                            for (toolCallReq in response.toolCalls) {
                                val args = parseToolArguments(toolCallReq.arguments)
                                val toolCall = ToolCall(
                                    id = toolCallReq.id,
                                    name = toolCallReq.name,
                                    arguments = args
                                )
                                val toolResult = toolExecutor.execute(toolCall)
                                val resultMessage = AssistantMessage.ToolCallResult(
                                    callId = toolCallReq.id,
                                    result = if (toolResult.success) toolResult.data else (toolResult.error ?: "Error"),
                                    isError = !toolResult.success
                                )
                                conversationHistory.add(resultMessage)
                            }
                            toolRounds++
                            continue
                        }

                        _lastResponse.value = response.content
                        _state.value = VoicePipelineState.Speaking
                        tts.speak(response.content)
                        _state.value = VoicePipelineState.Idle
                        return
                    }
                    else -> {
                        _state.value = VoicePipelineState.Idle
                        return
                    }
                }
            }

            _state.value = VoicePipelineState.Idle
        } catch (e: Exception) {
            Timber.e(e, "Voice pipeline error")
            _lastResponse.value = when {
                e.message?.contains("No available") == true ->
                    "No AI provider configured. Go to Settings to set up OpenClaw or a local LLM model."
                else -> "Something went wrong: ${e.message}"
            }
            _state.value = VoicePipelineState.Error(e.message ?: "Error")
            delay(4000)
            _state.value = VoicePipelineState.Idle
        }
    }

    fun interruptAndListen() {
        tts.stop()
        scope.launch { startListening() }
    }

    fun stopSpeaking() {
        tts.stop()
        _state.value = VoicePipelineState.Idle
    }

    fun clearHistory() {
        conversationHistory.clear()
        currentSession = null
    }

    private fun trimConversationHistory() {
        val maxMessages = 50
        if (conversationHistory.size > maxMessages) {
            val systemMessages = conversationHistory.filterIsInstance<AssistantMessage.System>()
            val recentMessages = conversationHistory.takeLast(maxMessages - systemMessages.size)
            conversationHistory.clear()
            conversationHistory.addAll(systemMessages + recentMessages)
        }
    }

    private fun startWatchdog() {
        cancelWatchdog()
        watchdogJob = scope.launch {
            delay(WATCHDOG_TIMEOUT_MS)
            if (isActive) {
                tts.stop()
                stt.stopListening()
                _state.value = VoicePipelineState.Idle
            }
        }
    }

    private fun resetWatchdog() { startWatchdog() }
    private fun cancelWatchdog() { watchdogJob?.cancel(); watchdogJob = null }

    @Suppress("UNCHECKED_CAST")
    private fun parseToolArguments(json: String): Map<String, Any?> {
        return try {
            moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any?> ?: emptyMap()
        } catch (e: Exception) { emptyMap() }
    }
}
