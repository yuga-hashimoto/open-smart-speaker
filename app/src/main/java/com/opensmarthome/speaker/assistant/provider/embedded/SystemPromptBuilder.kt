package com.opensmarthome.speaker.assistant.provider.embedded

import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.tool.ToolSchema

/**
 * Builds a complete prompt for the embedded LLM including system prompt,
 * tool definitions, conversation history, and proper turn markers.
 *
 * Uses Gemma chat template format by default.
 */
class SystemPromptBuilder {

    fun build(
        systemPrompt: String,
        messages: List<AssistantMessage>,
        tools: List<ToolSchema>,
        skillsXml: String = "",
        maxPromptChars: Int = DEFAULT_MAX_PROMPT_CHARS
    ): String {
        val sb = StringBuilder()

        // System turn
        sb.append("<start_of_turn>user\n")
        sb.append(systemPrompt)

        // Skills XML (OpenClaw pattern) — advertises available skills; LLM loads body on demand
        if (skillsXml.isNotBlank()) {
            sb.append("\n\n")
            sb.append(skillsXml)
            sb.append("\n\nWhen your task matches a skill's description, call `get_skill` with its name to load the full instructions.")
        }

        // Tool definitions
        if (tools.isNotEmpty()) {
            sb.append("\n\n")
            sb.append(buildToolSection(tools))
        }

        sb.append("<end_of_turn>\n")

        // Conversation history (truncated if needed)
        val historySection = buildHistorySection(messages)
        val availableChars = maxPromptChars - sb.length - MODEL_TURN_MARKER.length
        val truncatedHistory = truncateHistory(historySection, availableChars)
        sb.append(truncatedHistory)

        // End with model turn
        sb.append(MODEL_TURN_MARKER)

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
        return messages.map { msg ->
            when (msg) {
                is AssistantMessage.User ->
                    "<start_of_turn>user\n${msg.content}<end_of_turn>\n"

                is AssistantMessage.Assistant -> {
                    val toolCallStr = if (msg.toolCalls.isNotEmpty()) {
                        msg.toolCalls.joinToString("\n") { call ->
                            """{"tool_call": {"name": "${call.name}", "arguments": ${call.arguments}}}"""
                        }
                    } else {
                        msg.content
                    }
                    "<start_of_turn>model\n$toolCallStr<end_of_turn>\n"
                }

                is AssistantMessage.ToolCallResult ->
                    "<start_of_turn>user\n[Tool Result: ${msg.callId}]\n${msg.result}<end_of_turn>\n"

                is AssistantMessage.System ->
                    "<start_of_turn>user\n[System] ${msg.content}<end_of_turn>\n"

                is AssistantMessage.Delta -> ""
            }
        }
    }

    private fun truncateHistory(
        historyParts: List<String>,
        maxChars: Int
    ): String {
        if (maxChars <= 0) return ""

        // Always include the most recent messages first
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
        private const val MODEL_TURN_MARKER = "<start_of_turn>model\n"
    }
}
