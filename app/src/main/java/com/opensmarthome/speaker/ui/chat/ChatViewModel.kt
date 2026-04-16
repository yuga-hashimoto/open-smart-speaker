package com.opensmarthome.speaker.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.model.AssistantSession
import com.opensmarthome.speaker.assistant.model.ConversationState
import com.opensmarthome.speaker.assistant.model.ToolCallRequest
import com.opensmarthome.speaker.assistant.router.ConversationRouter
import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.voice.pipeline.VoicePipeline
import com.opensmarthome.speaker.voice.pipeline.VoicePipelineState
import com.opensmarthome.speaker.voice.stt.SpeechToText
import com.opensmarthome.speaker.voice.stt.SttResult
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val router: ConversationRouter,
    private val toolExecutor: ToolExecutor,
    private val moshi: Moshi,
    private val voicePipeline: VoicePipeline,
    private val stt: SpeechToText
) : ViewModel() {

    private val _messages = MutableStateFlow<List<AssistantMessage>>(emptyList())
    val messages: StateFlow<List<AssistantMessage>> = _messages.asStateFlow()

    private val _conversationState = MutableStateFlow<ConversationState>(ConversationState.Idle)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    private val _streamingContent = MutableStateFlow("")
    val streamingContent: StateFlow<String> = _streamingContent.asStateFlow()

    private var session: AssistantSession? = null

    val voiceState: StateFlow<VoicePipelineState> = voicePipeline.state

    companion object {
        private const val MAX_TOOL_ROUNDS = 10
    }

    fun startVoiceInput() {
        viewModelScope.launch {
            _conversationState.value = ConversationState.Listening

            var recognizedText = ""
            stt.startListening().collect { result ->
                when (result) {
                    is SttResult.Final -> recognizedText = result.text
                    is SttResult.Partial -> { /* UI could show partial */ }
                    is SttResult.Error -> {
                        _conversationState.value = ConversationState.Error(result.message)
                        return@collect
                    }
                }
            }

            if (recognizedText.isNotBlank()) {
                sendMessage(recognizedText)
            } else {
                _conversationState.value = ConversationState.Idle
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            val userMessage = AssistantMessage.User(content = text)
            _messages.value = _messages.value + userMessage
            _conversationState.value = ConversationState.Thinking

            try {
                val provider = router.resolveProvider(userInput = text)
                if (session == null) {
                    session = provider.startSession()
                }

                val tools = toolExecutor.availableTools()
                val conversationMessages = _messages.value.toMutableList()
                var toolRounds = 0

                while (toolRounds < MAX_TOOL_ROUNDS) {
                    _streamingContent.value = ""
                    val responseBuilder = StringBuilder()
                    val toolCalls = mutableListOf<ToolCallRequest>()

                    provider.sendStreaming(session!!, conversationMessages, tools)
                        .collect { delta ->
                            responseBuilder.append(delta.contentDelta)
                            _streamingContent.value = responseBuilder.toString()
                            delta.toolCallDelta?.let { toolCalls.add(it) }
                        }

                    val assistantResponse = AssistantMessage.Assistant(
                        content = responseBuilder.toString(),
                        toolCalls = toolCalls
                    )
                    conversationMessages.add(assistantResponse)

                    if (toolCalls.isNotEmpty()) {
                        for (toolCallReq in toolCalls) {
                            val args = parseToolArguments(toolCallReq.arguments)
                            val toolCall = ToolCall(
                                id = toolCallReq.id,
                                name = toolCallReq.name,
                                arguments = args
                            )
                            val toolResult = toolExecutor.execute(toolCall)
                            val resultMsg = AssistantMessage.ToolCallResult(
                                callId = toolCallReq.id,
                                result = if (toolResult.success) toolResult.data else (toolResult.error ?: "Error"),
                                isError = !toolResult.success
                            )
                            conversationMessages.add(resultMsg)
                        }
                        toolRounds++
                        continue
                    }

                    _messages.value = _messages.value + assistantResponse
                    _streamingContent.value = ""
                    _conversationState.value = ConversationState.Idle
                    return@launch
                }

                Timber.w("Max tool rounds ($MAX_TOOL_ROUNDS) reached")
                _streamingContent.value = ""
                _conversationState.value = ConversationState.Idle
            } catch (e: Exception) {
                Timber.e(e, "Failed to send message")
                _streamingContent.value = ""
                _conversationState.value = ConversationState.Error(e.message ?: "Unknown error")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseToolArguments(json: String): Map<String, Any?> {
        return try {
            moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any?> ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
