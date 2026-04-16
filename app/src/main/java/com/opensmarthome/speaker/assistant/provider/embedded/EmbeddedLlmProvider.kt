package com.opensmarthome.speaker.assistant.provider.embedded

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.model.AssistantSession
import com.opensmarthome.speaker.assistant.provider.AssistantProvider
import com.opensmarthome.speaker.assistant.provider.ProviderCapabilities
import com.opensmarthome.speaker.tool.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
        supportsTools = false,
        maxContextTokens = config.contextSize,
        modelName = File(config.modelPath).nameWithoutExtension
    )

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    override suspend fun startSession(config: Map<String, String>): AssistantSession {
        if (engine == null) {
            withContext(Dispatchers.IO) {
                initializeEngine()
            }
        }
        if (conversation == null) {
            createConversation()
        }
        return AssistantSession(providerId = id)
    }

    private fun initializeEngine() {
        val modelPath = this.config.modelPath

        // Try GPU first, fall back to CPU
        val gpuResult = runCatching {
            Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(),
                    cacheDir = context.cacheDir.absolutePath
                )
            ).apply { initialize() }
        }

        engine = gpuResult.getOrElse { gpuError ->
            Timber.w("GPU init failed: ${gpuError.message}, falling back to CPU")
            runCatching {
                Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(),
                        cacheDir = context.cacheDir.absolutePath
                    )
                ).apply { initialize() }
            }.getOrElse { cpuError ->
                throw IllegalStateException(
                    "Failed to initialize engine: GPU(${gpuError.message}), CPU(${cpuError.message})"
                )
            }
        }

        Timber.d("LiteRT-LM engine initialized")
    }

    private fun createConversation() {
        conversation?.close()
        conversation = engine?.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(this.config.systemPrompt),
                initialMessages = emptyList(),
                channels = emptyList()
            )
        )
        Timber.d("Conversation created")
    }

    override suspend fun endSession(session: AssistantSession) {
        conversation?.close()
        conversation = null
    }

    override suspend fun send(
        session: AssistantSession,
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>
    ): AssistantMessage = withContext(Dispatchers.IO) {
        val prompt = extractLastUserMessage(messages)
        val response = StringBuilder()

        conversation?.sendMessageAsync(prompt)?.collect { message ->
            response.append(message.contents.toString())
        }

        AssistantMessage.Assistant(content = response.toString().trim())
    }

    override fun sendStreaming(
        session: AssistantSession,
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>
    ): Flow<AssistantMessage.Delta> = flow {
        val prompt = extractLastUserMessage(messages)
        val response = StringBuilder()

        conversation?.sendMessageAsync(prompt)?.collect { message ->
            val chunk = message.contents.toString()
            if (chunk.isNotEmpty()) {
                response.append(chunk)
                emit(AssistantMessage.Delta(contentDelta = chunk))
            }
        }

        emit(AssistantMessage.Delta(finishReason = "stop"))
    }.flowOn(Dispatchers.IO)

    override suspend fun isAvailable(): Boolean {
        return engine != null || File(config.modelPath).exists()
    }

    override suspend fun latencyMs(): Long = 0L

    fun unload() {
        conversation?.close()
        conversation = null
        engine?.let {
            if (it.isInitialized()) it.close()
        }
        engine = null
    }

    private fun extractLastUserMessage(messages: List<AssistantMessage>): String {
        return (messages.lastOrNull { it is AssistantMessage.User } as? AssistantMessage.User)
            ?.content ?: "Hello"
    }
}
