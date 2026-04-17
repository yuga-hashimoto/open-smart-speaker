package com.opensmarthome.speaker.voice.pipeline

import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.provider.AssistantProvider
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Polishes a successful fast-path info-tool result into natural language via
 * a single-shot LLM call.
 *
 * Why:
 * - Open-Meteo returns English weather conditions ("Clear", "Partly cloudy")
 *   regardless of the user's language. The regex-based [FastPathResultFormatter]
 *   can't translate those, nor can it compose a truly natural reply.
 * - Feeding the raw JSON back to the LLM with a minimal prompt (no history,
 *   no tools, no system policy) keeps the round-trip cheap while giving the
 *   user an Alexa-class natural response.
 *
 * Scope: only the four info tools — `get_weather`, `get_forecast`,
 * `web_search`, `get_news`. Everything else (timers, volume, lights, routines)
 * already speaks a crisp [com.opensmarthome.speaker.voice.fastpath.FastPathMatch.spokenConfirmation]
 * or "Done."; a round-trip there would defeat the fast path's <200ms goal.
 *
 * Returns `null` on timeout, error, blank reply, or unsupported tool — the
 * caller must fall back to [FastPathResultFormatter] in that case.
 */
class FastPathLlmPolisher(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {

    /**
     * @param provider The active assistant provider (resolved by the router).
     *     The polisher uses a throw-away session so it does not pollute the
     *     main conversation history.
     * @param toolName Canonical tool name (`get_weather`, `get_forecast`,
     *     `web_search`, `get_news`). Unsupported tools return `null`.
     * @param userText The original user utterance (used for grounding the
     *     reply).
     * @param resultData The `ToolResult.data` JSON payload.
     * @param ttsLanguageTag BCP-47 tag such as `"ja-JP"`, `"en-US"`, or
     *     `null`. Drives the "respond in Japanese"/"respond in English"
     *     phrasing.
     */
    suspend fun polish(
        provider: AssistantProvider,
        toolName: String,
        userText: String,
        resultData: String,
        ttsLanguageTag: String?
    ): String? {
        if (toolName !in SUPPORTED_TOOLS) return null
        if (resultData.isBlank()) return null

        val prompt = buildPrompt(userText, resultData, ttsLanguageTag)

        return try {
            withTimeout(timeoutMs) {
                val session = provider.startSession()
                val reply = provider.send(
                    session,
                    listOf(AssistantMessage.User(content = prompt)),
                    emptyList()
                )
                provider.endSession(session)
                val content = (reply as? AssistantMessage.Assistant)?.content?.trim()
                content?.takeIf { it.isNotBlank() }
            }
        } catch (e: TimeoutCancellationException) {
            Timber.d("LLM polish timed out after ${timeoutMs}ms for $toolName")
            null
        } catch (e: Exception) {
            Timber.w(e, "LLM polish failed for $toolName")
            null
        }
    }

    /**
     * Builds a minimal, history-free prompt. The prompt deliberately inlines
     * the language directive so we don't need a system message and can keep
     * the turn to a single role=user message — fastest possible path through
     * any provider.
     */
    internal fun buildPrompt(
        userText: String,
        resultData: String,
        ttsLanguageTag: String?
    ): String {
        val languageDirective = languageDirective(ttsLanguageTag)
        return buildString {
            append("User asked: ")
            append(userText)
            append("\n\n")
            append("Tool result (JSON): ")
            append(resultData)
            append("\n\n")
            append(languageDirective)
            append(
                " Answer naturally in 1-2 short sentences suitable for " +
                    "a smart-speaker TTS reply. Do not repeat the JSON, do " +
                    "not apologize, do not mention that you used a tool."
            )
        }
    }

    private fun languageDirective(tag: String?): String {
        val normalized = tag?.lowercase().orEmpty()
        return when {
            normalized.startsWith("ja") -> "日本語で答えてください。"
            normalized.startsWith("zh") -> "请用中文回答。"
            normalized.startsWith("ko") -> "한국어로 답해 주세요."
            normalized.startsWith("es") -> "Responde en español."
            normalized.startsWith("fr") -> "Réponds en français."
            normalized.startsWith("de") -> "Antworte auf Deutsch."
            else -> "Respond in English."
        }
    }

    companion object {
        /**
         * 20 seconds — Gemma 4 E2B (2B params, on-device) typically takes
         * 5-10s to produce a 1-2 sentence polish reply. The previous 4s budget
         * timed out almost every request on real devices, which caused the
         * fast path to silently fall back to the raw regex formatter (English
         * weather conditions, no translation). 20s keeps us comfortably above
         * p99 generation time while still giving up if the model is wedged.
         */
        const val DEFAULT_TIMEOUT_MS: Long = 20_000L

        val SUPPORTED_TOOLS: Set<String> = setOf(
            "get_weather",
            "get_forecast",
            "web_search",
            "get_news"
        )
    }
}
