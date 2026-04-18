package com.opendash.app.assistant.provider.embedded

import com.opendash.app.voice.pipeline.FastPathResultFormatter

/**
 * Collapses a tool's raw JSON [ToolCallResult.result] into a compact,
 * plain-text grounding block the on-device LLM can reliably consume in the
 * second round of the agent loop.
 *
 * Why: Gemma-class models (2B / 2-4B params) regularly emit the bare
 * "..." failure mode when fed the raw JSON from `web_search` /
 * `get_weather` / `get_forecast` / `get_news`. The verbose payloads push
 * them past the short context window, confuse their attention, or trigger
 * the "continuation" token loop. Short, structured text ("Abstract: …",
 * "Current: …", "Headlines: …") lets the same model produce a natural
 * 1-2 sentence answer without any prompt-engineering voodoo.
 *
 * The summaries re-use the battle-tested [FastPathResultFormatter.buildPolishSummary]
 * extractors so the same grounding keeps working in two places:
 *   1. fast-path LLM polish round ([FastPathLlmPolisher])
 *   2. agent-loop second round (this class)
 *
 * Everything else (timers, device control, skills …) passes through the
 * raw result because it's already compact and usually not text the LLM
 * is expected to re-summarize for the user.
 */
class ToolResultSummarizer {

    /**
     * @param toolName canonical tool name (e.g. `web_search`, `get_weather`).
     * @param rawData the `ToolResult.data` payload from the executor.
     * @return a non-blank summary suitable for injection into the 2nd-round
     *     prompt. Guaranteed to be ≤ [MAX_SUMMARY_CHARS] characters so we
     *     never blow the model's context on a single tool result.
     */
    fun summarize(toolName: String, rawData: String): String {
        if (rawData.isBlank()) return EMPTY_MARKER

        // Pre-truncate before running the regex-based formatter. Regex
        // backtracking on unterminated JSON string literals can trigger
        // a StackOverflowError on Linux JVMs with small default stacks.
        // We append `"}` after truncation so the regex engine always finds
        // a closing quote, preventing catastrophic backtracking.
        val safeData = if (rawData.length > FORMATTER_INPUT_CAP) {
            rawData.substring(0, FORMATTER_INPUT_CAP) + "\"}"
        } else {
            rawData
        }

        val summary = when (toolName) {
            in FORMATTER_TOOLS -> {
                val built = FastPathResultFormatter.buildPolishSummary(
                    toolName = toolName,
                    data = safeData,
                    ttsLanguageTag = null
                )
                built.ifBlank { safeData }
            }
            else -> safeData
        }

        return truncate(summary)
    }

    private fun truncate(s: String): String {
        if (s.length <= MAX_SUMMARY_CHARS) return s
        // Reserve room for the ellipsis so the hard cap is never exceeded.
        val body = s.substring(0, MAX_SUMMARY_CHARS - ELLIPSIS.length).trimEnd()
        return body + ELLIPSIS
    }

    companion object {
        /**
         * Absolute upper bound for the injected grounding block. Gemma
         * 2-4B's sweet spot for follow-up answering is ~200-600 chars; we
         * leave headroom for the directive + user question.
         */
        const val MAX_SUMMARY_CHARS = 800

        /**
         * Hard cap on the input we feed to [FastPathResultFormatter]'s
         * regex-based field extractors. Kept deliberately small (500 chars)
         * so that even a pathological `abstract` value of thousands of
         * characters cannot trigger catastrophic regex backtracking /
         * `StackOverflowError` in the `"key":"((?:\\.|[^"\\])*)"` matcher.
         * Linux CI JVMs have a smaller default thread stack than macOS,
         * making them susceptible at values that appear safe locally.
         * All real-world `web_search` / `get_weather` payloads that matter
         * (key + a 1-2 sentence abstract) fit comfortably within 500 chars.
         */
        private const val FORMATTER_INPUT_CAP = 500

        private const val ELLIPSIS = "…"

        /**
         * Returned when the tool produced no data at all. Keeps the
         * grounding block non-blank so the downstream "answer from the
         * result" directive still applies and the model doesn't fall
         * through to "..."
         */
        private const val EMPTY_MARKER = "(tool returned no data)"

        /** Tools whose raw data benefits from summarization. */
        private val FORMATTER_TOOLS = setOf(
            "web_search",
            "get_weather",
            "get_forecast",
            "get_news"
        )
    }
}
