package com.opensmarthome.speaker.assistant.provider.embedded

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.model.AssistantSession
import com.opensmarthome.speaker.assistant.model.ToolCallRequest
import com.opensmarthome.speaker.assistant.provider.AssistantProvider
import com.opensmarthome.speaker.assistant.provider.ProviderCapabilities
import com.opensmarthome.speaker.tool.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class EmbeddedLlmProvider(
    private val context: Context,
    private val config: EmbeddedLlmConfig
) : AssistantProvider {

    override val id: String = "embedded_llm"
    override val displayName: String = "On-Device LLM"
    override val capabilities = ProviderCapabilities(
        supportsStreaming = true,
        supportsTools = true,
        maxContextTokens = config.contextSize,
        modelName = File(config.modelPath).nameWithoutExtension
    )

    private var inference: LlmInference? = null

    override suspend fun startSession(config: Map<String, String>): AssistantSession {
        if (inference == null) {
            withContext(Dispatchers.IO) {
                loadModel()
            }
        }
        return AssistantSession(providerId = id)
    }

    override suspend fun endSession(session: AssistantSession) {
        // Keep model loaded
    }

    override suspend fun send(
        session: AssistantSession,
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>
    ): AssistantMessage = withContext(Dispatchers.IO) {
        val llm = inference ?: throw IllegalStateException("Model not loaded")
        val prompt = buildPrompt(messages, tools)
        val response = llm.generateResponse(prompt)
        parseResponse(response)
    }

    override fun sendStreaming(
        session: AssistantSession,
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>
    ): Flow<AssistantMessage.Delta> = flow {
        val llm = inference ?: throw IllegalStateException("Model not loaded")
        val prompt = buildPrompt(messages, tools)

        val tokenChannel = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)

        val future = llm.generateResponseAsync(prompt) { partialResult, done ->
            if (partialResult.isNotEmpty()) {
                tokenChannel.trySend(partialResult)
            }
            if (done) {
                tokenChannel.close()
            }
        }

        for (token in tokenChannel) {
            emit(AssistantMessage.Delta(contentDelta = token))
        }
        emit(AssistantMessage.Delta(finishReason = "stop"))
    }.flowOn(Dispatchers.IO)

    override suspend fun isAvailable(): Boolean {
        return inference != null || File(config.modelPath).exists()
    }

    override suspend fun latencyMs(): Long = 0L

    fun unload() {
        inference?.close()
        inference = null
    }

    private fun loadModel() {
        val modelFile = File(config.modelPath)
        if (!modelFile.exists()) {
            throw IllegalStateException("Model file not found: ${config.modelPath}")
        }

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(config.modelPath)
            .setMaxTokens(config.maxTokens)
            .setMaxTopK(40)
            .build()

        inference = LlmInference.createFromOptions(context, options)
        Timber.d("MediaPipe LLM loaded: ${modelFile.name} (${modelFile.length() / 1_048_576}MB)")
    }

    private fun buildPrompt(messages: List<AssistantMessage>, tools: List<ToolSchema>): String {
        val sb = StringBuilder()

        sb.append("<start_of_turn>user\n")
        sb.append(config.systemPrompt)

        if (tools.isNotEmpty()) {
            sb.append("\n\nAvailable tools:\n")
            for (tool in tools) {
                sb.append("- ${tool.name}: ${tool.description}\n")
                sb.append("  Parameters: ${tool.parameters.entries.joinToString { "${it.key}: ${it.value.description}" }}\n")
            }
            sb.append("\nTo call a tool, respond with JSON: {\"tool\": \"name\", \"arguments\": {...}}\n")
        }
        sb.append("<end_of_turn>\n")

        for (msg in messages) {
            when (msg) {
                is AssistantMessage.User -> sb.append("<start_of_turn>user\n${msg.content}<end_of_turn>\n")
                is AssistantMessage.Assistant -> sb.append("<start_of_turn>model\n${msg.content}<end_of_turn>\n")
                is AssistantMessage.System -> sb.append("<start_of_turn>user\n[System: ${msg.content}]<end_of_turn>\n")
                is AssistantMessage.ToolCallResult -> sb.append("<start_of_turn>user\n[Tool Result: ${msg.result}]<end_of_turn>\n")
                is AssistantMessage.Delta -> {}
            }
        }

        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun parseResponse(response: String): AssistantMessage {
        val trimmed = response.trim()
        val toolCallRegex = """\{"tool"\s*:\s*"(\w+)"\s*,\s*"arguments"\s*:\s*(\{[^}]*\})\}""".toRegex()
        val match = toolCallRegex.find(trimmed)

        return if (match != null) {
            AssistantMessage.Assistant(
                content = trimmed,
                toolCalls = listOf(
                    ToolCallRequest(
                        id = "call_${java.lang.System.currentTimeMillis()}",
                        name = match.groupValues[1],
                        arguments = match.groupValues[2]
                    )
                )
            )
        } else {
            AssistantMessage.Assistant(content = trimmed)
        }
    }
}
