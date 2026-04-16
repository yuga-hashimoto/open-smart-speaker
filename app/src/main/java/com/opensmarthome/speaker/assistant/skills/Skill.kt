package com.opensmarthome.speaker.assistant.skills

/**
 * A SKILL.md file provides task-specific instructions to the agent.
 *
 * Inspired by OpenClaw's skills system (src/agents/skills/).
 * Skills are registered in the system prompt as <available_skills> XML,
 * and the agent loads the full SKILL.md body on demand when the task matches.
 */
data class Skill(
    val name: String,
    val description: String,
    val body: String,
    val source: String = "bundled"
)
