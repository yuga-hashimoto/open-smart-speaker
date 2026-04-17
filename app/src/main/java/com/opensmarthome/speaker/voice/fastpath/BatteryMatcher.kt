package com.opensmarthome.speaker.voice.fastpath

import com.opensmarthome.speaker.util.BatteryMonitor

/**
 * Stateful fast-path matcher for "battery status" questions. Reads the
 * current BatteryStatus directly and returns a spoken-only response — no
 * tool call, no LLM round-trip. Sub-200ms target like other fast paths.
 *
 * Lives outside [FastPathMatchers.kt] because it needs a BatteryMonitor
 * instance; DI wiring attaches it after the stateless default matchers.
 */
class BatteryMatcher(private val batteryMonitor: BatteryMonitor) : FastPathMatcher {

    private val englishPatterns = listOf(
        Regex("""(?:what(?:'s|\s+is)\s+)?(?:my\s+)?battery(?:\s+level|\s+status|\s+life)?"""),
        Regex("""how\s+much\s+battery"""),
        Regex("""charge\s+(?:level|percentage)""")
    )
    private val japanesePatterns = listOf(
        Regex("""バッテリー(?:残量|レベル|状態)?"""),
        Regex("""電池(?:残量|レベル)?""")
    )

    override fun tryMatch(normalized: String): FastPathMatch? {
        val matched = englishPatterns.any { it.containsMatchIn(normalized) } ||
            japanesePatterns.any { it.containsMatchIn(normalized) }
        if (!matched) return null

        val status = batteryMonitor.status.value
        val level = status.level
        val charging = status.isCharging
        val isJapanese = japanesePatterns.any { it.containsMatchIn(normalized) }
        val reply = if (isJapanese) {
            if (charging) "バッテリー残量は${level}％、充電中です。"
            else "バッテリー残量は${level}％です。"
        } else {
            if (charging) "Battery is at $level percent and charging."
            else "Battery is at $level percent."
        }
        return FastPathMatch(toolName = null, spokenConfirmation = reply)
    }
}
