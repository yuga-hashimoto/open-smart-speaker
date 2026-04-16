package com.opensmarthome.speaker.assistant.provider.embedded

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.opensmarthome.speaker.assistant.context.DeviceContextBuilder
import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.model.AssistantSession
import com.opensmarthome.speaker.assistant.provider.AssistantProvider
import com.opensmarthome.speaker.assistant.provider.ProviderCapabilities
import com.opensmarthome.speaker.assistant.skills.SkillRegistry
import com.opensmarthome.speaker.device.DeviceManager
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
    private val config: EmbeddedLlmConfig,
    private val skillRegistry: SkillRegistry? = null,
    private val deviceManager: DeviceManager? = null
) : AssistantProvider {

    private val deviceContextBuilder = DeviceContextBuilder()

    override val id: String = "embedded_llm"
    override val displayName: String = "On-Device LLM"
    override val capabilities = ProviderCapabilities(
        supportsStreaming = true,
        // The agent loop parses tool calls from model output (ToolCallParser),
        // so we declare tool support. Not every model is good at it, but
        // VoicePipeline's tool loop works regardless.
        supportsTools = true,
        maxContextTokens = config.contextSize,
        modelName = File(config.modelPath).nameWithoutExtension,
        supportsVision = detectVisionSupport(File(config.modelPath).nameWithoutExtension),
        isLocal = true
    )

    private fun detectVisionSupport(modelName: String): Boolean {
        val lower = modelName.lowercase()
        // Gemma 3n (E2B/E4B) and Gemma 4 with -mm variants support vision.
        return "gemma-3n" in lower || "gemma3n" in lower || "-mm" in lower || "vision" in lower
    }

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

    private suspend fun initializeEngine() {
        val modelPath = this.config.modelPath

        val initializer = EngineInitializer()
        val result = initializer.initialize(
            initGpu = {
                Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.GPU(),
                        cacheDir = context.cacheDir.absolutePath
                    )
                ).apply { initialize() }
            },
            initCpu = {
                Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(),
                        cacheDir = context.cacheDir.absolutePath
                    )
                ).apply { initialize() }
            }
        )

        engine = when (result) {
            is EngineInitializer.Result.Success -> {
                Timber.d("LiteRT-LM engine initialized on ${result.backend}")
                result.engine
            }
            is EngineInitializer.Result.Failure -> throw IllegalStateException(
                "Failed to initialize engine: GPU(${result.gpuError}), CPU(${result.cpuError})"
            )
        }
    }

    private fun createConversation() {
        conversation?.close()
        conversation = engine?.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(buildSystemInstruction()),
                initialMessages = emptyList(),
                channels = emptyList()
            )
        )
        Timber.d("Conversation created (skills=${skillRegistry?.all()?.size ?: 0})")
    }

    /**
     * Build system instruction with optional skill XML injected.
     * OpenClaw-style: skills are advertised; LLM requests bodies on demand via get_skill.
     */
    private fun buildSystemInstruction(): String {
        val base = this.config.systemPrompt
        val skillsXml = skillRegistry?.toPromptXml().orEmpty()
        if (skillsXml.isBlank()) return base
        return buildString {
            append(base)
            append("\n\n")
            append(skillsXml)
            append("\n\nWhen your task matches a skill's description, call `get_skill` with its name to load the full instructions.")
        }
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
        val prompt = buildEnrichedPrompt(messages)
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
        val prompt = buildEnrichedPrompt(messages)
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

    /**
     * Prepends a compact device state snapshot to the user's message so the
     * agent knows which devices exist and their current state without
     * needing to call a tool first.
     */
    private fun buildEnrichedPrompt(messages: List<AssistantMessage>): String {
        val userMessage = extractLastUserMessage(messages)
        val devices = deviceManager?.devices?.value?.values ?: return userMessage
        if (devices.isEmpty()) return userMessage

        val ctx = deviceContextBuilder.build(devices)
        if (ctx.isBlank()) return userMessage

        return buildString {
            append(ctx)
            append("\n\n")
            append(userMessage)
        }
    }
}
