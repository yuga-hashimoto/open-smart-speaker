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
        sb.appendLine(
            "You MUST trust this tool list. If the user's request can be answered " +
                "by calling a tool, you MUST call it — never reply \"I don't have tools\" " +
                "or \"I can't do that\"; that is forbidden. Call the tool first, then " +
                "answer the user using the tool result."
        )
        sb.appendLine()
        sb.appendLine(AUTONOMOUS_AGENT_DIRECTIVE)
        sb.appendLine()
        sb.appendLine("Emit a tool call using any of these equivalent formats:")
        sb.appendLine("""  (A) {"tool_call": {"name": "<tool>", "arguments": {<args>}}}""")
        sb.appendLine("""  (B) {"name": "<tool>", "arguments": {<args>}}""")
        sb.appendLine("""  (C) TOOL_CALL: <tool>(arg1="value", arg2=42)""")
        sb.appendLine("After a tool result is returned, continue reasoning and respond to the user in plain text.")
        sb.appendLine()

        val toolNames = tools.map { it.name }.toSet()
        if ("web_search" in toolNames) {
            // Small on-device LLMs (e.g. Gemma 2B) often hallucinate "I can't
            // search the web" even when web_search is listed. Spelling it out
            // as an IMPORTANT directive makes the tool-use behavior reliable.
            sb.appendLine(
                "IMPORTANT: If the user asks you to search, look up, find information online, " +
                    "ググって, 検索, について, とは, or similar open-ended information queries, " +
                    "you MUST call the `web_search` tool. Do NOT say you cannot search — " +
                    "the tool is available."
            )
            sb.appendLine()
        }

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

        val examples = buildFewShotExamples(tools)
        if (examples.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## Examples (study these, then follow the same pattern)")
            examples.forEach { sb.appendLine(it) }
        }
        return sb.toString()
    }

    /**
     * Diverse few-shot examples across common tool categories (ja + en).
     * Only includes examples for tools that are actually exposed so the LLM
     * doesn't learn to call tools that aren't in the current filtered list.
     */
    private fun buildFewShotExamples(tools: List<ToolSchema>): List<String> {
        val available = tools.map { it.name }.toSet()
        val candidates = listOf(
            FewShot(
                tool = "get_weather",
                user = "What's the weather in Tokyo today?",
                call = """{"tool_call": {"name": "get_weather", "arguments": {"location": "Tokyo"}}}"""
            ),
            FewShot(
                tool = "get_weather",
                user = "今日の東京の天気は？",
                call = """{"tool_call": {"name": "get_weather", "arguments": {"location": "東京"}}}"""
            ),
            FewShot(
                tool = "web_search",
                user = "Tell me about the Webb telescope.",
                call = """{"tool_call": {"name": "web_search", "arguments": {"query": "James Webb Space Telescope"}}}"""
            ),
            FewShot(
                tool = "web_search",
                user = "量子コンピューターについて詳しく",
                call = """{"tool_call": {"name": "web_search", "arguments": {"query": "量子コンピューター 概要"}}}"""
            ),
            FewShot(
                tool = "web_search",
                user = "最新のニュースを検索して",
                call = """{"tool_call": {"name": "web_search", "arguments": {"query": "最新のニュース"}}}"""
            ),
            FewShot(
                tool = "set_timer",
                user = "5分タイマーかけて",
                call = """TOOL_CALL: set_timer(seconds=300, label="タイマー")"""
            ),
            FewShot(
                tool = "set_volume",
                user = "音量を半分にして",
                call = """{"name": "set_volume", "arguments": {"level": 50}}"""
            ),
            FewShot(
                tool = "get_news",
                user = "ニュースを教えて",
                call = """{"tool_call": {"name": "get_news", "arguments": {"topic": "top"}}}"""
            ),
            FewShot(
                tool = "execute_command",
                user = "Turn on the living-room lights.",
                call = """{"tool_call": {"name": "execute_command", "arguments": {"device_id": "light.living_room", "action": "turn_on"}}}"""
            )
        )
        val chosen = candidates.filter { it.tool in available }
        if (chosen.isEmpty()) return emptyList()
        return chosen.take(MAX_FEW_SHOT).map { shot ->
            "User: ${shot.user}\nAssistant: ${shot.call}"
        }
    }

    private data class FewShot(val tool: String, val user: String, val call: String)

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
        private const val MAX_FEW_SHOT = 6

        /**
         * Reinforces autonomous multi-tool agent behavior. Small on-device
         * models (Gemma 4 E2B / 2B class) tend to stop after a single tool
         * call and declare the task done even when another tool is needed.
         * This directive tells them to keep chaining tools until they can
         * answer, and to never refuse on ability.
         */
        internal const val AUTONOMOUS_AGENT_DIRECTIVE =
            "You are an autonomous assistant capable of using multiple tools in " +
                "sequence to help the user. If a single tool is not enough, call " +
                "additional tools until you can answer. Do not stop after one tool " +
                "call if the result is incomplete — chain tools when needed. Do not " +
                "say \"I cannot\" or refuse; try tools first. When a tool result is " +
                "in the conversation, use it to answer the user in natural language, " +
                "in the user's language."
    }
}
