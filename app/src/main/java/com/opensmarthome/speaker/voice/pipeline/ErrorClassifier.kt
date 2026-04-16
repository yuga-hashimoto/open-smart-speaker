package com.opensmarthome.speaker.voice.pipeline

/**
 * Classifies an error message / exception cause into a user-facing category
 * with short, spoken-friendly copy. Avoids technical jargon.
 *
 * Offline-first: if the active provider is LOCAL (embedded LLM / on-device),
 * network-shaped errors are remapped to a local-engine category so users never
 * get "Network hiccup" copy for a purely local failure.
 */
class ErrorClassifier {

    data class Recovery(
        val category: Category,
        val userSpokenMessage: String,
        val canRetry: Boolean
    )

    enum class Category {
        NO_PROVIDER,
        STT_FAILURE,
        LLM_TIMEOUT,
        NETWORK,
        LOCAL_ENGINE,
        PERMISSION,
        TOOL_FAILURE,
        UNKNOWN
    }

    /**
     * Whether the currently active provider runs on-device (LOCAL) or needs
     * the internet (REMOTE). When UNKNOWN, we fall back to keyword-only heuristics.
     */
    enum class ProviderKind { LOCAL, REMOTE, UNKNOWN }

    fun classify(
        raw: String?,
        cause: Throwable? = null,
        kind: ProviderKind = ProviderKind.UNKNOWN
    ): Recovery {
        val lower = (raw?.lowercase().orEmpty() + " " +
            cause?.message?.lowercase().orEmpty())
            .replace('_', ' ') // ERROR_NO_MATCH → "error no match"

        val base = when {
            contains(lower, "no available", "no provider", "model not", "llm not") ->
                Recovery(
                    Category.NO_PROVIDER,
                    "I don't have an AI model ready yet. Open settings to download one.",
                    canRetry = false
                )
            contains(lower, "gguf", "failed to load model", "model load", "out of memory") ->
                Recovery(
                    Category.LOCAL_ENGINE,
                    "The on-device model had trouble. Let me try again.",
                    canRetry = true
                )
            contains(lower, "index out of range", "no match", "speech timeout", "list index") ->
                Recovery(
                    Category.STT_FAILURE,
                    "Sorry, I didn't catch that. Try again?",
                    canRetry = true
                )
            contains(lower, "timeout", "timed out", "deadline") ->
                Recovery(
                    Category.LLM_TIMEOUT,
                    "That took too long. Let me try again.",
                    canRetry = true
                )
            contains(lower, "unable to resolve", "connection", "unreachable", "host") ->
                Recovery(
                    Category.NETWORK,
                    "Network hiccup. Checking again.",
                    canRetry = true
                )
            contains(lower, "permission", "not granted", "denied") ->
                Recovery(
                    Category.PERMISSION,
                    "I need permission for that. Check settings.",
                    canRetry = false
                )
            contains(lower, "tool", "execution failed", "arguments") ->
                Recovery(
                    Category.TOOL_FAILURE,
                    "That didn't work. Want me to try a different way?",
                    canRetry = true
                )
            else -> Recovery(
                Category.UNKNOWN,
                "Something went wrong. Try again?",
                canRetry = true
            )
        }

        // Offline-first: suppress NETWORK copy when we know the active
        // provider is purely on-device. Tool-level errors for network tools
        // (weather, search) go through TOOL_FAILURE instead, so we don't lose
        // signal — we just stop blaming the network for LLM-side issues.
        if (kind == ProviderKind.LOCAL && base.category == Category.NETWORK) {
            return Recovery(
                Category.LOCAL_ENGINE,
                "The on-device model had trouble. Let me try again.",
                canRetry = true
            )
        }
        return base
    }

    private fun contains(haystack: String, vararg needles: String): Boolean =
        needles.any { it in haystack }
}
