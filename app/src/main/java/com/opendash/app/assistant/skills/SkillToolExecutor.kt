package com.opendash.app.assistant.skills

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema

/**
 * Exposes the skill registry as LLM-callable tools.
 *
 * - get_skill: fetch the full SKILL.md body to inline into the agent's context
 * - list_skills: list available skills (also visible via system prompt XML)
 * - install_skill_from_url: download + register a SKILL.md from a URL
 */
class SkillToolExecutor(
    private val registry: SkillRegistry,
    private val installer: SkillInstaller? = null
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> {
        val tools = mutableListOf<ToolSchema>()
        if (!registry.isEmpty()) {
            tools.add(ToolSchema(
                name = "get_skill",
                description = "Load the full instructions for a skill. Use when your current task matches a skill listed in <available_skills>.",
                parameters = mapOf(
                    "name" to ToolParameter("string", "The skill name to load", required = true)
                )
            ))
            tools.add(ToolSchema(
                name = "list_skills",
                description = "List all available skills and their descriptions.",
                parameters = emptyMap()
            ))
        }
        if (installer != null) {
            tools.add(ToolSchema(
                name = "install_skill_from_url",
                description = "Download and register a new SKILL.md from a URL. The URL must point to a raw SKILL.md with YAML frontmatter (name, description).",
                parameters = mapOf(
                    "url" to ToolParameter("string", "Direct URL to SKILL.md content", required = true)
                )
            ))
        }
        return tools
    }

    override suspend fun execute(call: ToolCall): ToolResult {
        return when (call.name) {
            "get_skill" -> executeGet(call)
            "list_skills" -> executeList(call)
            "install_skill_from_url" -> executeInstall(call)
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

    private suspend fun executeInstall(call: ToolCall): ToolResult {
        val url = call.arguments["url"] as? String
            ?: return ToolResult(call.id, false, "", "Missing url")
        val inst = installer
            ?: return ToolResult(call.id, false, "", "Skill installer not available")

        return when (val result = inst.installFromUrl(url)) {
            is SkillInstaller.Result.Installed -> ToolResult(
                call.id, true,
                """{"installed":"${result.skill.name.escapeJson()}","description":"${result.skill.description.escapeJson()}"}"""
            )
            is SkillInstaller.Result.Failed -> ToolResult(call.id, false, "", result.reason)
        }
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
}
