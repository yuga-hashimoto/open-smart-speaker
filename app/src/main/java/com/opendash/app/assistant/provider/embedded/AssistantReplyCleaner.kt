package com.opendash.app.assistant.provider.embedded

/**
 * Strip leaked chat-template role markers from LLM output.
 *
 * On-device Gemma variants occasionally emit the *next* turn's role marker
 * (e.g. "User: ..." or "<|assistant|>\n...") as the first token of their
 * own reply, then hit the stop token almost immediately. The raw tail
 * reaches TTS and the speaker says "User..." to the user — audible,
 * confusing, and clearly wrong.
 *
 * This cleaner runs on assembled assistant content and removes the leading
 * role-marker noise while preserving any real content after it. If nothing
 * but a marker is present, the result is blank; upstream logic decides
 * whether to retry, fall back, or stay silent.
 *
 * Kept as a pure `object` with a single `cleanContent` entry point so it's
 * trivially testable and safe to call from multiple places (provider
 * streaming loop, retry policy, final send assembly, etc.).
 */
object AssistantReplyCleaner {

    /**
     * Remove every recognised leading role-marker from [raw] and return
     * the trimmed remainder. Safe to call on null-ish / blank input.
     *
     * Strip rules applied repeatedly until the head stabilises:
     *   - `User` / `Assistant` / `Model` / `System` (bare, optional `:` and ellipsis)
     *   - `<user>` / `<assistant>` / `<model>` / `<system>`
     *   - `[user]` / `[assistant]` / `[model]` / `[system]`
     *   - `<|user|>` / `<|assistant|>` / `<|system|>`
     *   - `<start_of_turn>user` / `<start_of_turn>model` / `<end_of_turn>`
     *   - `<|im_start|>` / `<|im_end|>` / `<|eot_id|>`
     *
     * Matching is case-insensitive so `USER:` / `user:` / `User:` all fall.
     * Middle-of-sentence occurrences of the literal word "user" are left
     * alone (e.g. "The user guide is open.").
     */
    fun cleanContent(raw: String): String {
        if (raw.isBlank()) return ""
        var current = raw.trim()
        while (true) {
            val stripped = stripOnce(current).trim()
            if (stripped == current) return current
            current = stripped
        }
    }

    private fun stripOnce(input: String): String {
        for (pattern in LEADING_MARKER_PATTERNS) {
            val match = pattern.find(input) ?: continue
            if (match.range.first != 0) continue
            return input.substring(match.range.last + 1)
        }
        return input
    }

    // Order matters only in the sense that `stripOnce` short-circuits at the
    // first hit — patterns here are mutually exclusive at position 0 so we
    // don't rely on priority. IGNORE_CASE lets `user` / `USER` / `User` all
    // match the same pattern.
    private val LEADING_MARKER_PATTERNS: List<Regex> = listOf(
        // ChatML / llama-style pipe markers — must be consumed with their
        // optional newline so the body starts cleanly.
        Regex("""^<\|(?:user|assistant|system|model)\|>\s*\n?""", RegexOption.IGNORE_CASE),
        Regex("""^<\|im_start\|>[^\n]*\n?""", RegexOption.IGNORE_CASE),
        Regex("""^<\|im_end\|>\s*""", RegexOption.IGNORE_CASE),
        Regex("""^<\|eot_id\|>\s*""", RegexOption.IGNORE_CASE),

        // Gemma turn markers: `<start_of_turn>user\n` / `<start_of_turn>model\n`.
        Regex(
            """^<start_of_turn>(?:user|model|assistant|system)\s*\n?""",
            RegexOption.IGNORE_CASE
        ),
        Regex("""^<end_of_turn>\s*""", RegexOption.IGNORE_CASE),

        // Simple angle-bracket tags: `<user>`, `<model>`, `<assistant>`, `<system>`.
        Regex("""^<(?:user|assistant|model|system)>\s*""", RegexOption.IGNORE_CASE),

        // Bracket tags: `[USER]`, `[assistant]`, etc.
        Regex("""^\[(?:user|assistant|model|system)]\s*""", RegexOption.IGNORE_CASE),

        // Bare role name, anchored at start, optional colon and trailing
        // ellipsis/punctuation. Requires a word boundary after so we don't
        // eat "user guide". The optional `:` (or ellipsis, or end-of-string)
        // is what distinguishes a role marker from ordinary prose.
        Regex(
            """^(?:user|assistant|model|system)\b(?:\s*:\s*|\s*\.{3,}\s*|\s*$)""",
            RegexOption.IGNORE_CASE
        )
    )
}
