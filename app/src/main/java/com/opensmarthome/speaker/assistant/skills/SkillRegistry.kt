package com.opensmarthome.speaker.assistant.skills

import timber.log.Timber

/**
 * Holds the set of skills available to the agent.
 * Provides XML-formatted prompt injection (OpenClaw compatible):
 *
 * <available_skills>
 *   <skill>
 *     <name>...</name>
 *     <description>...</description>
 *   </skill>
 * </available_skills>
 *
 * When a task matches a skill's description, the LLM can request the full
 * skill body via the `get_skill` tool (provided by SkillToolExecutor).
 */
class SkillRegistry {

    private val skills = mutableMapOf<String, Skill>()

    fun register(skill: Skill) {
        if (skills.containsKey(skill.name)) {
            Timber.d("Overwriting skill: ${skill.name}")
        }
        skills[skill.name] = skill
    }

    fun registerAll(newSkills: Collection<Skill>) {
        newSkills.forEach { register(it) }
    }

    fun get(name: String): Skill? = skills[name]

    fun all(): List<Skill> = skills.values.toList()

    fun isEmpty(): Boolean = skills.isEmpty()

    /**
     * Build the XML skill listing for system prompt injection.
     * Returns an empty string when no skills are registered.
     */
    fun toPromptXml(): String {
        if (skills.isEmpty()) return ""
        val items = skills.values.joinToString("\n") { skill ->
            """  <skill>
    <name>${skill.name.escapeXml()}</name>
    <description>${skill.description.escapeXml()}</description>
  </skill>"""
        }
        return "<available_skills>\n$items\n</available_skills>"
    }

    private fun String.escapeXml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
