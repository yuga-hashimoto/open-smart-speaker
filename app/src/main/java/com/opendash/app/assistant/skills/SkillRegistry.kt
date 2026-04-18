package com.opendash.app.assistant.skills

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
    private val disabled = mutableSetOf<String>()

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

    fun unregister(name: String): Boolean {
        disabled.remove(name)
        return skills.remove(name) != null
    }

    fun all(): List<Skill> = skills.values.toList()

    fun isEmpty(): Boolean = skills.isEmpty()

    /** Returns only skills that are currently enabled (the default state). */
    fun enabled(): List<Skill> = skills.values.filter { it.name !in disabled }

    fun isEnabled(name: String): Boolean = skills.containsKey(name) && name !in disabled

    fun setEnabled(name: String, enabled: Boolean) {
        if (!skills.containsKey(name)) return
        if (enabled) disabled.remove(name) else disabled.add(name)
    }

    /**
     * Build the XML skill listing for system prompt injection.
     * Only includes enabled skills — disabled ones stay out of the prompt so
     * the LLM won't reference them.
     */
    fun toPromptXml(): String {
        val active = enabled()
        if (active.isEmpty()) return ""
        val items = active.joinToString("\n") { skill ->
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
