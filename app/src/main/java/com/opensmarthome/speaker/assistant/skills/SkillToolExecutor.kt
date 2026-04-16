package com.opensmarthome.speaker.assistant.skills

import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolParameter
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.tool.ToolSchema

/**
 * Exposes the skill registry as LLM-callable tools.
 *
 * - get_skill: fetch the full SKILL.md body to inline into the agent's context
 * - list_skills: list available skills (also visible via system prompt XML)
 */
class SkillToolExecutor(
    private val registry: SkillRegistry
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> {
        if (registry.isEmpty()) return emptyList()
        return listOf(
            ToolSchema(
                name = "get_skill",
                description = "Load the full instructions for a skill. Use this when your current task matches a skill listed in <available_skills>.",
                parameters = mapOf(
                    "name" to ToolParameter("string", "The skill name to load", required = true)
                )
            ),
            ToolSchema(
                name = "list_skills",
                description = "List all available skills and their descriptions.",
                parameters = emptyMap()
            )
        )
    }

    override suspend fun execute(call: ToolCall): ToolResult {
        return when (call.name) {
            "get_skill" -> executeGet(call)
            "list_skills" -> executeList(call)
            else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
        }
    }

    private fun executeGet(call: ToolCall): ToolResult {
        val name = call.arguments["name"] as? String
            ?: return ToolResult(call.id, false, "", "Missing name parameter")

        val skill = registry.get(name)
            ?: return ToolResult(call.id, false, "", "Skill not found: $name")

        val data = """{"name":"${skill.name.escapeJson()}","description":"${skill.description.escapeJson()}","body":"${skill.body.escapeJson()}"}"""
        return ToolResult(call.id, true, data)
    }

    private fun executeList(call: ToolCall): ToolResult {
        val items = registry.all().joinToString(",") { s ->
            """{"name":"${s.name.escapeJson()}","description":"${s.description.escapeJson()}"}"""
        }
        return ToolResult(call.id, true, "[$items]")
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
}
