package com.opensmarthome.speaker.voice.fastpath

/**
 * Classifies user utterances as "ambiguous information queries" that should
 * skip the fast-path and be handed to the LLM so it can reason about which
 * tools to chain.
 *
 * Why this exists: the fast-path matchers are tuned for latency (≤200 ms)
 * on the Alexa-class command surface (timers / volume / lights / weather /
 * news). Information-seeking utterances like "トマトって何？" or "explain
 * quantum computing" are not commands — they benefit from the LLM's
 * autonomous multi-tool reasoning (web_search → summarize → follow-up).
 * Letting them fall through the fast path preserves agency for these cases
 * while keeping explicit commands instant.
 *
 * Design contract:
 * - **Ambiguous** = open-ended "what / how / why / about / とは / について /
 *   教えて / 何" question with NO explicit tool-verb token.
 * - **Explicit** = utterance contains one of the tool-verb tokens (検索,
 *   タイマー, 天気, 予報, ニュース, volume, timer, search, weather…). These
 *   always go through the fast path first.
 *
 * This util is purely informational — it does not return a match; it only
 * tells the router whether to *skip* matching. The actual matchers remain
 * the source of truth when they fire.
 */
object AgentIntentHint {

    /**
     * Tool-verb tokens that force fast-path handling even when the
     * utterance looks like an information request.
     *
     * Keep this list tight — adding a token here means the utterance bypasses
     * LLM routing. The list intentionally covers the canonical commands that
     * every speaker user needs within 200 ms (Priority 1 — smart-home feel).
     */
    private val explicitToolTokens: List<String> = listOf(
        // Japanese explicit verbs / nouns
        "検索", "ググ", "タイマー", "天気", "てんき", "予報",
        "ニュース", "アラーム", "音量", "ボリューム", "電気", "照明",
        "つけて", "消して", "教えて天気", "教えて予報",
        // Explicit system / device queries that existing fast-path matchers
        // already handle. Keeping them here prevents info-query markers like
        // "教えて" from stealing the utterance from the matcher.
        "現在地", "位置", "場所",
        "できること", "何ができる", "ヘルプ",
        "今何時", "時刻", "今の時間", "今日の日付",
        "バッテリー", "充電", "電池",
        "通知", "リマインダー",
        "予定", "カレンダー",
        // English explicit verbs / nouns
        "search", "google", "look up", "timer", "weather", "forecast",
        "news", "alarm", "volume", "louder", "quieter",
        "lights", "turn on", "turn off",
        // English equivalents of the system/device queries above.
        "where am i", "my location", "current location",
        "what can you do", "help",
        "what time", "time is it", "today's date",
        "battery", "charge",
        "notifications",
        "calendar",
        // Volume / media control tokens
        "mute", "unmute", "pause", "play", "skip", "next", "previous"
    )

    /**
     * Tokens that signal the utterance is an open-ended information request
     * without a concrete tool in mind. Presence of any of these (and ABSENCE
     * of any [explicitToolTokens]) → ambiguous.
     */
    private val informationQueryTokens: List<String> = listOf(
        // Japanese open-question markers
        "とは", "について", "ってなに", "って何", "って?", "って？",
        "教えて", "なぜ", "どうして", "どうやって", "どうやっ",
        "作り方", "方法", "違い",
        // English open-question markers
        "what is", "what's a", "what are", "what does",
        "tell me about", "explain", "how do", "how to", "how does",
        "why is", "why does", "why do", "difference between"
    )

    /**
     * True when [utterance] looks like an open information question. Callers
     * (the fast-path router) can treat `true` as "skip fast-path; let the
     * LLM route".
     *
     * Returns false when the utterance is empty, when it contains an explicit
     * tool-verb token, or when it does not contain any information-query
     * marker.
     */
    fun isAmbiguousInformationQuery(utterance: String): Boolean {
        if (utterance.isBlank()) return false
        val normalized = utterance.trim().lowercase()

        // Explicit tool verb wins — route via fast-path matchers as usual.
        if (explicitToolTokens.any { normalized.contains(it.lowercase()) }) {
            return false
        }

        // Must contain at least one information-query marker.
        return informationQueryTokens.any { normalized.contains(it.lowercase()) }
    }
}
