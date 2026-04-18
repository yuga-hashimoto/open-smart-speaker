package com.opendash.app.assistant.proactive

/**
 * A rule that inspects the current context and optionally produces a Suggestion.
 * Rules are composable — the SuggestionEngine runs them all and collects results.
 */
interface SuggestionRule {
    suspend fun evaluate(context: ProactiveContext): Suggestion?
}

/**
 * Context passed to suggestion rules. Includes what's knowable proactively:
 * time of day, device states, recent events, etc.
 */
data class ProactiveContext(
    val nowMs: Long,
    val hourOfDay: Int,
    val dayOfWeek: Int,
    val deviceStates: Map<String, Any?> = emptyMap(),
    val lastUserInteractionMs: Long? = null
)
