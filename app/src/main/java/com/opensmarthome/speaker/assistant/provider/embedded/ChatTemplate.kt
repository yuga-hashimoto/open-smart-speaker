package com.opensmarthome.speaker.assistant.provider.embedded

import com.opensmarthome.speaker.assistant.model.AssistantMessage

/**
 * Chat templates convert a list of AssistantMessage into a model-specific prompt string.
 *
 * Supported:
 *   - GEMMA (default): <start_of_turn>user ... <end_of_turn> / <start_of_turn>model
 *   - QWEN: <|im_start|>user ... <|im_end|> / <|im_start|>assistant
 *   - LLAMA3: <|start_header_id|>user<|end_header_id|> ... <|eot_id|>
 *   - CHATML: same as QWEN (OpenAI ChatML format)
 *
 * LiteRT-LM applies its own template internally when Conversation is used,
 * so ChatTemplate is used when rendering messages for llama.cpp-style
 * raw-prompt inference.
 */
interface ChatTemplate {
    val id: String
    fun renderTurn(role: Role, content: String): String
    fun modelTurnMarker(): String

    enum class Role { SYSTEM, USER, MODEL, TOOL_RESULT }

    companion object {
        fun forModelName(name: String?): ChatTemplate {
            val lower = name?.lowercase().orEmpty()
            return when {
                "qwen" in lower -> QwenTemplate
                "llama-3" in lower || "llama3" in lower -> Llama3Template
                "chatml" in lower -> QwenTemplate
                else -> GemmaTemplate
            }
        }
    }
}

object GemmaTemplate : ChatTemplate {
    override val id: String = "gemma"

    override fun renderTurn(role: ChatTemplate.Role, content: String): String {
        // Gemma only has user/model turns; system/tool folded into user
        val turnRole = when (role) {
            ChatTemplate.Role.MODEL -> "model"
            else -> "user"
        }
        return "<start_of_turn>$turnRole\n$content<end_of_turn>\n"
    }

    override fun modelTurnMarker(): String = "<start_of_turn>model\n"
}

object QwenTemplate : ChatTemplate {
    override val id: String = "qwen"

    override fun renderTurn(role: ChatTemplate.Role, content: String): String {
        val turnRole = when (role) {
            ChatTemplate.Role.SYSTEM -> "system"
            ChatTemplate.Role.USER -> "user"
            ChatTemplate.Role.MODEL -> "assistant"
            ChatTemplate.Role.TOOL_RESULT -> "tool"
        }
        return "<|im_start|>$turnRole\n$content<|im_end|>\n"
    }

    override fun modelTurnMarker(): String = "<|im_start|>assistant\n"
}

object Llama3Template : ChatTemplate {
    override val id: String = "llama3"

    override fun renderTurn(role: ChatTemplate.Role, content: String): String {
        val turnRole = when (role) {
            ChatTemplate.Role.SYSTEM -> "system"
            ChatTemplate.Role.USER -> "user"
            ChatTemplate.Role.MODEL -> "assistant"
            ChatTemplate.Role.TOOL_RESULT -> "tool"
        }
        return "<|start_header_id|>$turnRole<|end_header_id|>\n\n$content<|eot_id|>"
    }

    override fun modelTurnMarker(): String = "<|start_header_id|>assistant<|end_header_id|>\n\n"
}

/** Render a list of AssistantMessage using the given template. */
fun ChatTemplate.renderMessages(messages: List<AssistantMessage>): String {
    val sb = StringBuilder()
    for (msg in messages) {
        val (role, content) = when (msg) {
            is AssistantMessage.User -> ChatTemplate.Role.USER to msg.content
            is AssistantMessage.Assistant -> ChatTemplate.Role.MODEL to msg.content
            is AssistantMessage.System -> ChatTemplate.Role.SYSTEM to msg.content
            is AssistantMessage.ToolCallResult ->
                ChatTemplate.Role.TOOL_RESULT to "[Tool ${msg.callId}]\n${msg.result}"
            is AssistantMessage.Delta -> continue
        }
        sb.append(renderTurn(role, content))
    }
    return sb.toString()
}
