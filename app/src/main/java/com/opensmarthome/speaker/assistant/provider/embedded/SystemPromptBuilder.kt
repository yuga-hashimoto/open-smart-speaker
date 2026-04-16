package com.opensmarthome.speaker.assistant.provider.embedded

import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.tool.ToolSchema

/**
 * Builds a complete prompt for the embedded LLM including system prompt,
 * tool definitions, conversation history, and proper turn markers.
 *
 * Template is pluggable (Gemma / Qwen / Llama3 / ChatML) via ChatTemplate.
 * Defaults to Gemma.
 */
class SystemPromptBuilder(
    private val template: ChatTemplate = GemmaTemplate
) {

    fun build(
        systemPrompt: String,
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>,
        skillsXml: String = "",
        maxPromptChars: Int = DEFAULT_MAX_PROMPT_CHARS
    ): String {
        val sb = StringBuilder()

        // System turn content
        val systemContent = buildString {
            append(systemPrompt)

            if (skillsXml.isNotBlank()) {
                append("\n\n")
                append(skillsXml)
                append("\n\nWhen your task matches a skill's description, call `get_skill` with its name to load the full instructions.")
            }

            if (tools.isNotEmpty()) {
                append("\n\n")
                append(buildToolSection(tools))
            }
        }
        sb.append(template.renderTurn(ChatTemplate.Role.SYSTEM, systemContent))

        // Conversation history (truncated if needed)
        val historyParts = buildHistorySection(messages)
        val modelMarker = template.modelTurnMarker()
        val availableChars = maxPromptChars - sb.length - modelMarker.length
        val truncatedHistory = truncateHistory(historyParts, availableChars)
        sb.append(truncatedHistory)

        // End with model turn
        sb.append(modelMarker)

        return sb.toString()
    }

    private fun buildToolSection(tools: List<ToolSchema>): String {
        val sb = StringBuilder()
        sb.appendLine("## Available Tools")
        sb.appendLine("You can call tools by responding with a JSON object in this format:")
        sb.appendLine("""{"tool_call": {"name": "<tool_name>", "arguments": {<args>}}}""")
        sb.appendLine()
        sb.appendLine("After a tool result is returned, continue reasoning and respond to the user.")
        sb.appendLine()

        for (tool in tools) {
            sb.appendLine("### ${tool.name}")
            sb.appendLine(tool.description)
            if (tool.parameters.isNotEmpty()) {
                sb.appendLine("Parameters:")
                for ((name, param) in tool.parameters) {
                    val req = if (param.required) " (required)" else " (optional)"
                    val enumStr = param.enum?.let { " [${it.joinToString(", ")}]" } ?: ""
                    sb.appendLine("  - $name: ${param.type}$req — ${param.description}$enumStr")
                }
            }
        }
        return sb.toString()
    }

    private fun buildHistorySection(messages: List<AssistantMessage>): List<String> {
        return messages.mapNotNull { msg ->
            when (msg) {
                is AssistantMessage.User ->
                    template.renderTurn(ChatTemplate.Role.USER, msg.content)

                is AssistantMessage.Assistant -> {
                    val content = if (msg.toolCalls.isNotEmpty()) {
                        msg.toolCalls.joinToString("\n") { call ->
                            """{"tool_call": {"name": "${call.name}", "arguments": ${call.arguments}}}"""
                        }
                    } else {
                        msg.content
                    }
                    template.renderTurn(ChatTemplate.Role.MODEL, content)
                }

                is AssistantMessage.ToolCallResult ->
                    template.renderTurn(
                        ChatTemplate.Role.TOOL_RESULT,
                        "[Tool Result: ${msg.callId}]\n${msg.result}"
                    )

                is AssistantMessage.System ->
                    template.renderTurn(ChatTemplate.Role.SYSTEM, msg.content)

                is AssistantMessage.Delta -> null
            }
        }
    }

    private fun truncateHistory(
        historyParts: List<String>,
        maxChars: Int
    ): String {
        if (maxChars <= 0) return ""

        val reversed = historyParts.reversed()
        val selected = mutableListOf<String>()
        var totalChars = 0

        for (part in reversed) {
            if (totalChars + part.length > maxChars) break
            selected.add(part)
            totalChars += part.length
        }

        return selected.reversed().joinToString("")
    }

    companion object {
        private const val DEFAULT_MAX_PROMPT_CHARS = 3000
    }
}
