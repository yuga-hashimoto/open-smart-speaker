package com.opensmarthome.speaker.voice.pipeline

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.model.AssistantSession
import com.opensmarthome.speaker.assistant.router.ConversationRouter
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.voice.stt.AndroidSttProvider
import com.opensmarthome.speaker.voice.stt.SpeechToText
import com.opensmarthome.speaker.voice.stt.SttResult
import com.opensmarthome.speaker.voice.tts.AndroidTtsProvider
import com.opensmarthome.speaker.voice.tts.TextToSpeech
import com.opensmarthome.speaker.service.VoiceService
import com.opensmarthome.speaker.voice.wakeword.WakeWordDetector
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class VoicePipeline(
    private val context: Context,
    private val stt: SpeechToText,
    private val tts: TextToSpeech,
    private val router: ConversationRouter,
    private val toolExecutor: ToolExecutor,
    private val moshi: Moshi,
    private val preferences: AppPreferences,
    private val wakeWordDetector: WakeWordDetector? = null
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

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var toneGenerator: ToneGenerator? = null

    companion object {
        private const val MAX_TOOL_ROUNDS = 10
        private const val WATCHDOG_TIMEOUT_MS = 5 * 60 * 1000L
        private const val CONTINUOUS_MODE_DELAY_MS = 500L
        private const val DEFAULT_SILENCE_TIMEOUT_MS = 1500L
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
        // Barge-in handling
        if (_state.value is VoicePipelineState.Speaking) {
            val bargeInEnabled = preferences.observe(PreferenceKeys.BARGE_IN_ENABLED).first() ?: true
            if (!bargeInEnabled) {
                Timber.d("Barge-in disabled, ignoring mic tap during speech")
                return
            }
            tts.stop()
        }
        stt.stopListening()
        _partialText.value = ""
        _lastResponse.value = ""

        // Apply STT settings from preferences (the binding is lost across calls)
        applySttPreferences()
        applyTtsLanguagePreference()

        // Pause wake word detection to release microphone (OpenClaw broadcast pattern)
        VoiceService.pauseHotword(context)

        requestAudioFocus()
        playListeningBeep()
        // Wait for beep to finish and mic to be fully released
        delay(500)

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
                        abandonAudioFocus()
                        delay(2000)
                        resumeWakeWord()
                        _state.value = VoicePipelineState.Idle
                        return@collect
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "STT failed")
            _lastResponse.value = "Voice recognition unavailable."
            _state.value = VoicePipelineState.Error(e.message ?: "STT error")
            abandonAudioFocus()
            delay(2000)
            resumeWakeWord()
            _state.value = VoicePipelineState.Idle
            return
        }

        Timber.d("STT finalText='$finalText' (blank=${finalText.isBlank()})")
        if (finalText.isNotBlank()) {
            processUserInput(finalText)
        } else {
            abandonAudioFocus()
            resumeWakeWord()
            Timber.d("No speech detected, returning to Idle")
            _state.value = VoicePipelineState.Idle
        }
    }

    suspend fun processUserInput(text: String) {
        Timber.d("processUserInput called with: '$text'")
        _state.value = VoicePipelineState.Processing
        _partialText.value = text
        _lastResponse.value = ""
        resetWatchdog()

        // Play thinking sound if enabled
        playThinkingSound()

        try {
            val provider = router.resolveProvider()
            Timber.d("Provider resolved: ${provider.id}")
            if (currentSession == null) {
                Timber.d("Creating new session...")
                currentSession = provider.startSession()
                Timber.d("Session created")
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
                        Timber.d("Speaking response: ${response.content.take(50)}...")

                        // Check if TTS is enabled
                        val ttsEnabled = preferences.observe(PreferenceKeys.TTS_ENABLED).first() ?: true
                        if (ttsEnabled) {
                            _state.value = VoicePipelineState.Speaking
                            try {
                                tts.speak(response.content)
                                Timber.d("TTS completed")
                            } catch (e: Exception) {
                                Timber.e(e, "TTS failed")
                            }
                        }

                        // Continuous conversation mode
                        val continuousMode = preferences.observe(PreferenceKeys.CONTINUOUS_MODE).first() ?: false
                        if (continuousMode) {
                            Timber.d("Continuous mode: restarting listening after delay")
                            delay(CONTINUOUS_MODE_DELAY_MS)
                            startListening()
                        } else {
                            abandonAudioFocus()
                            resumeWakeWord()
                            _state.value = VoicePipelineState.Idle
                        }
                        return
                    }
                    else -> {
                        abandonAudioFocus()
                        resumeWakeWord()
                        _state.value = VoicePipelineState.Idle
                        return
                    }
                }
            }

            abandonAudioFocus()
            resumeWakeWord()
            _state.value = VoicePipelineState.Idle
        } catch (e: Exception) {
            Timber.e(e, "Voice pipeline error")
            _lastResponse.value = when {
                e.message?.contains("No available") == true ->
                    "No AI provider configured. Go to Settings to set up OpenClaw or download an on-device model."
                else -> "Something went wrong: ${e.message}"
            }
            abandonAudioFocus()
            _state.value = VoicePipelineState.Error(e.message ?: "Error")
            delay(4000)
            resumeWakeWord()
            _state.value = VoicePipelineState.Idle
        }
    }

    fun showError(message: String) {
        _lastResponse.value = message
        _state.value = VoicePipelineState.Error(message)
        scope.launch {
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
        abandonAudioFocus()
        resumeWakeWord()
        _state.value = VoicePipelineState.Idle
    }

    fun clearHistory() {
        conversationHistory.clear()
        currentSession = null
    }

    // --- Audio Focus ---

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attrs)
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    // --- Thinking Sound ---

    private suspend fun playThinkingSound() {
        val enabled = preferences.observe(PreferenceKeys.THINKING_SOUND).first() ?: true
        if (!enabled) return

        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            Timber.w("Could not play thinking sound: ${e.message}")
        }
    }

    private fun playListeningBeep() {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 100)
        } catch (e: Exception) {
            Timber.w("Could not play listening beep: ${e.message}")
        }
    }

    // --- Wake Word Resume ---

    private fun resumeWakeWord() {
        VoiceService.resumeHotword(context)
    }

    // --- Preference Application ---

    private suspend fun applySttPreferences() {
        val stt = this.stt as? AndroidSttProvider ?: return
        val sttLang = preferences.observe(PreferenceKeys.STT_LANGUAGE).first()?.takeIf { it.isNotBlank() }
        val silence = preferences.observe(PreferenceKeys.SILENCE_TIMEOUT_MS).first() ?: 1500L
        stt.language = sttLang
        stt.silenceTimeoutMs = silence
        Timber.d("STT prefs applied: lang=$sttLang, silence=${silence}ms")
    }

    private suspend fun applyTtsLanguagePreference() {
        val lang = preferences.observe(PreferenceKeys.TTS_LANGUAGE).first()?.takeIf { it.isNotBlank() } ?: return
        when (val t = this.tts) {
            is com.opensmarthome.speaker.voice.tts.TtsManager -> t.setLanguage(lang)
            is AndroidTtsProvider -> t.setLanguage(lang)
            else -> { /* other providers pull lang from prefs at speak time */ }
        }
    }

    // --- Conversation History ---

    private fun trimConversationHistory() {
        val maxMessages = 50
        if (conversationHistory.size > maxMessages) {
            val systemMessages = conversationHistory.filterIsInstance<AssistantMessage.System>()
            val recentMessages = conversationHistory.takeLast(maxMessages - systemMessages.size)
            conversationHistory.clear()
            conversationHistory.addAll(systemMessages + recentMessages)
        }
    }

    // --- Watchdog ---

    private fun startWatchdog() {
        cancelWatchdog()
        watchdogJob = scope.launch {
            delay(WATCHDOG_TIMEOUT_MS)
            if (isActive) {
                tts.stop()
                stt.stopListening()
                abandonAudioFocus()
                _state.value = VoicePipelineState.Idle
            }
        }
    }

    private fun resetWatchdog() { startWatchdog() }
    private fun cancelWatchdog() { watchdogJob?.cancel(); watchdogJob = null }

    // --- Tool Arguments ---

    @Suppress("UNCHECKED_CAST")
    private fun parseToolArguments(json: String): Map<String, Any?> {
        return try {
            moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any?> ?: emptyMap()
        } catch (e: Exception) { emptyMap() }
    }

    fun destroy() {
        toneGenerator?.release()
        toneGenerator = null
        abandonAudioFocus()
    }
}
