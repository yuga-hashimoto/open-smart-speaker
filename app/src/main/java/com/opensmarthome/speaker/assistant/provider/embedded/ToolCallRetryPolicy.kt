package com.opensmarthome.speaker.assistant.provider.embedded

import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.model.ToolCallRequest
import com.opensmarthome.speaker.tool.ToolSchema

/**
 * Decides whether the on-device LLM's first attempt needs a stricter retry
 * and composes the final `AssistantMessage.Assistant` from the parsed
 * response(s).
 *
 * Pure, injectable, and testable without a real LiteRT-LM engine. The
 * provider supplies the actual `send` side-effect as a suspending lambda.
 */
class ToolCallRetryPolicy(
    private val parser: ToolCallParser = ToolCallParser()
) {

    /**
     * Run the (already-executed) first attempt through parsing + refusal
     * detection and, if needed, call [retryFn] to obtain a second attempt.
     *
     * [retryFn] is invoked at most once when any of the following hold:
     *  - the first attempt did not produce any tool calls, AND
     *  - [tools] is non-empty AND the first attempt looks like a refusal
     *    (see [ToolCallParser.looksLikeRefusal]), OR
     *  - the first attempt is empty / whitespace-only / bare ellipsis
     *    (see [looksEmpty]) — this is the 2nd-round agent-loop failure
     *    mode where Gemma 2B chokes on the raw tool result JSON.
     *
     * Guarantees at most one retry.
     */
    suspend fun finalize(
        firstAttempt: String,
        tools: List<ToolSchema>,
        retryFn: suspend () -> String
    ): AssistantMessage.Assistant {
        val parsedFirst = parser.parse(firstAttempt)
        if (parsedFirst.toolCalls.isNotEmpty()) {
            return assistant(parsedFirst.text, parsedFirst.toolCalls)
        }

        val shouldRetryOnRefusal =
            tools.isNotEmpty() && ToolCallParser.looksLikeRefusal(firstAttempt)
        val shouldRetryOnEmpty = looksEmpty(parsedFirst.text, firstAttempt)

        if (!shouldRetryOnRefusal && !shouldRetryOnEmpty) {
            return assistant(parsedFirst.text.ifBlank { firstAttempt.trim() }, emptyList())
        }

        val retryResponse = retryFn()
        val parsedRetry = parser.parse(retryResponse)
        if (parsedRetry.toolCalls.isNotEmpty()) {
            return assistant(parsedRetry.text, parsedRetry.toolCalls)
        }
        // Prefer the retry text, then the first attempt, then a generic
        // non-blank placeholder so the TTS never speaks a literal empty
        // string.
        val fallbackText = parsedRetry.text
            .ifBlank { parsedFirst.text.ifBlank { firstAttempt.trim() } }
            .ifBlank { EMPTY_RESPONSE_FALLBACK }
        return assistant(fallbackText, emptyList())
    }

    /**
     * Build the final `AssistantMessage.Assistant`. Runs the content through
     * [AssistantReplyCleaner] so leaked Gemma role markers (`User:`,
     * `<|assistant|>`, bare `User...`) never reach TTS — Bug B fix.
     */
    private fun assistant(
        content: String,
        toolCalls: List<ToolCallRequest>
    ): AssistantMessage.Assistant =
        AssistantMessage.Assistant(
            content = AssistantReplyCleaner.cleanContent(content),
            toolCalls = toolCalls
        )

    companion object {
        /** Minimum length a "real" reply must have to skip the empty retry. */
        private const val MIN_NONEMPTY_CHARS = 10

        /**
         * Characters we consider noise. When the response is made up
         * entirely of these, it's Gemma's "..." failure mode — re-prompt.
         */
        private val NOISE_CHARS = setOf(
            '.', '…', '·', '•', ' ', '\t', '\n', '\r'
        )

        /**
         * Non-blank placeholder spoken when both the first attempt and the
         * retry return bare ellipses. Keeps the user informed rather than
         * silent. Intentionally language-neutral; the higher-level
         * VoicePipeline fallback handles localization when the round cap
         * is hit.
         */
        private const val EMPTY_RESPONSE_FALLBACK =
            "I could not generate a response from the tool result."

        /**
         * True when the LLM produced essentially nothing useful — bare
         * `...`, `…`, whitespace, or fewer than [MIN_NONEMPTY_CHARS] chars
         * of noise. Checks both the parsed text (after XML/TOOL_CALL
         * stripping) and the raw response so we never fail to catch a
         * degenerate output regardless of which parser branch consumed it.
         *
         * Kept `internal` so tests can pin edge cases directly.
         */
        internal fun looksEmpty(parsedText: String, rawResponse: String): Boolean {
            val text = parsedText.ifBlank { rawResponse }.trim()
            if (text.isEmpty()) return true
            if (text.all { it in NOISE_CHARS }) return true
            if (text.length < MIN_NONEMPTY_CHARS && text.all { it in NOISE_CHARS || it == '.' }) {
                return true
            }
            return false
        }
    }
}
