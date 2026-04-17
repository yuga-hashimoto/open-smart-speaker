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
    private val toolCallParser = ToolCallParser()
    private val retryPolicy = ToolCallRetryPolicy(toolCallParser)
    private val systemPromptBuilder = SystemPromptBuilder()

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

    /**
     * Pre-warm the engine off the main thread so the first user request
     * doesn't pay the GPU/CPU init cost. Safe to call from app start —
     * idempotent (subsequent calls no-op once engine is up).
     *
     * Returns true on success, false on init failure (caller can fall back
     * to the legacy lazy path).
     */
    suspend fun warmUp(): Boolean = withContext(Dispatchers.IO) {
        if (engine != null) return@withContext true
        try {
            initializeEngine()
            if (conversation == null) createConversation()
            Timber.d("EmbeddedLlmProvider warmed up")
            true
        } catch (e: Exception) {
            Timber.w(e, "EmbeddedLlmProvider warmup failed")
            false
        }
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
        val firstAttempt = sendOnce(messages, tools, retry = false)
        retryPolicy.finalize(
            firstAttempt = firstAttempt,
            tools = tools
        ) {
            Timber.d("Refusal detected in LLM output; retrying with stricter directive")
            sendOnce(messages, tools, retry = true)
        }
    }

    private suspend fun sendOnce(
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>,
        retry: Boolean
    ): String {
        val prompt = buildEnrichedPrompt(messages, tools, retry)
        val response = StringBuilder()
        conversation?.sendMessageAsync(prompt)?.collect { message ->
            response.append(message.contents.toString())
        }
        return response.toString()
    }

    override fun sendStreaming(
        session: AssistantSession,
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>
    ): Flow<AssistantMessage.Delta> = flow {
        val prompt = buildEnrichedPrompt(messages, tools, retry = false)
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
     * Builds the per-turn user prompt. Structure:
     *   1. (optional) tool annex — directive + tool list + few-shot examples
     *      since LiteRT-LM conversations have a fixed system instruction.
     *   2. (optional) device state snapshot.
     *   3. The user's last message.
     *
     * When [retry] is true the tool annex is reinforced with a stricter
     * directive that forbids "I don't have tools" and instructs the model
     * to pick exactly one tool from the list.
     */
    private fun buildEnrichedPrompt(
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>,
        retry: Boolean
    ): String {
        val userMessage = extractLastUserMessage(messages)
        val sb = StringBuilder()

        if (tools.isNotEmpty()) {
            sb.append(buildToolAnnex(tools, retry))
            sb.append("\n\n")
        }

        val devices = deviceManager?.devices?.value?.values
        if (!devices.isNullOrEmpty()) {
            val ctx = deviceContextBuilder.build(devices)
            if (ctx.isNotBlank()) {
                sb.append(ctx)
                sb.append("\n\n")
            }
        }

        sb.append(userMessage)
        return sb.toString()
    }

    /**
     * Builds a compact tool prompt annex. Re-uses [SystemPromptBuilder]'s
     * tool section so the few-shot examples and format directives stay in
     * one place.
     */
    private fun buildToolAnnex(tools: List<ToolSchema>, retry: Boolean): String {
        // SystemPromptBuilder builds a full prompt; we only need the tool
        // section text, so we feed an empty system prompt and empty history
        // and then slice out the tool portion. Cheaper to inline the builder
        // format here, but re-using keeps one source of truth.
        val full = systemPromptBuilder.build(
            systemPrompt = "",
            messages = emptyList(),
            tools = tools
        )
        val toolSection = extractToolSection(full)
        if (!retry) return toolSection
        return buildString {
            append(toolSection)
            append("\n\n")
            append(RETRY_DIRECTIVE)
        }
    }

    private fun extractToolSection(full: String): String {
        // SystemPromptBuilder wraps the content in chat-template tokens.
        // We simply find the "## Available Tools" block.
        val idx = full.indexOf("## Available Tools")
        if (idx == -1) return ""
        val tail = full.substring(idx)
        // Trim any trailing template markers.
        val endTokens = listOf("<end_of_turn>", "<|im_end|>", "<|eot_id|>")
        var cutoff = tail.length
        for (token in endTokens) {
            val pos = tail.indexOf(token)
            if (pos in 0 until cutoff) cutoff = pos
        }
        return tail.substring(0, cutoff).trimEnd()
    }

    companion object {
        private const val RETRY_DIRECTIVE =
            "IMPORTANT: Your previous reply refused the request. You MUST NOT " +
                "say \"I don't have tools\" or \"I can't\". Look at the tool list " +
                "above and pick EXACTLY ONE tool whose description matches the " +
                "user's request, then emit a tool call now using one of the " +
                "supported formats. Do not apologize — just emit the tool call."
    }
}
