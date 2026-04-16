package com.opensmarthome.speaker.assistant.routine

/**
 * A named sequence of tool actions the user can invoke or schedule.
 *
 * Examples:
 *   "good_night" → [turn off all lights, lock doors, set alarm 7am]
 *   "coming_home" → [turn on lights, set AC to 22, play music]
 *
 * Routines are persisted user-defined workflows — a simpler cousin of
 * OpenClaw's cron_tool for scheduled tasks.
 */
data class Routine(
    val id: String,
    val name: String,
    val description: String,
    val actions: List<RoutineAction>
)

data class RoutineAction(
    val toolName: String,
    val arguments: Map<String, Any?>,
    /** Optional delay (ms) before this action runs (after the previous). */
    val delayMs: Long = 0L
)
