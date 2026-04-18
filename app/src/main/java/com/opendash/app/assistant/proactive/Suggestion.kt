package com.opendash.app.assistant.proactive

/**
 * A proactive suggestion the agent can surface without user prompt.
 * Examples:
 *   - "Good morning. Would you like me to turn on the lights?"
 *   - "You have a meeting in 10 minutes."
 *   - "The front door is still open."
 */
data class Suggestion(
    val id: String,
    val priority: Priority,
    val message: String,
    /** Optional tool the user can trigger with one tap/voice confirmation. */
    val suggestedAction: SuggestedAction? = null,
    val expiresAtMs: Long? = null
) {
    enum class Priority { LOW, NORMAL, HIGH, URGENT }
}

data class SuggestedAction(
    val toolName: String,
    val arguments: Map<String, Any?>
)
