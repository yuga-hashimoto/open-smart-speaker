package com.opensmarthome.speaker.voice.fastpath

import com.opensmarthome.speaker.tool.system.TimerInfo
import com.opensmarthome.speaker.tool.system.TimerManager
import kotlinx.coroutines.runBlocking

/**
 * Stateful fast-path matcher for "cancel the <label> timer" utterances.
 *
 * Resolves the user's spoken label to an active timer id by snapshotting
 * [TimerManager.getActiveTimers] at match time. When no active timer's
 * label contains the spoken label (case-insensitive), returns null so the
 * utterance falls through to the LLM — cheaper than inventing an id.
 *
 * Ordering in [DefaultFastPathRouter] (see DI wiring):
 *  - Registered AFTER [CancelAllTimersMatcher] so "cancel all timers" wins
 *    its dedicated broad path. This matcher also explicitly short-circuits
 *    on "all" / "every" / "全部" / "全て" to avoid eating that case.
 *  - Registered BEFORE [TimerMatcher] so specific-label cancels don't get
 *    swallowed by the generic duration-based timer regex.
 *
 * Lives outside [FastPathMatchers.kt] because it needs a [TimerManager]
 * instance; DI wiring slots it into the router after the stateless
 * default matchers.
 *
 * [getActiveTimers] is `suspend`; we snapshot it synchronously inside
 * [tryMatch] via `runBlocking`. Acceptable here because the matcher is
 * called from the fast-path hot loop off the main thread, the in-process
 * AndroidTimerManager implementation returns immediately from its in-
 * memory map, and [FastPathMatcher.tryMatch] is a synchronous contract.
 */
class CancelTimerByLabelMatcher(
    private val timerManager: TimerManager
) : FastPathMatcher {

    private val englishPatterns = listOf(
        Regex("""cancel\s+(?:the\s+)?(.+?)\s+timer"""),
        Regex("""stop\s+(?:the\s+)?(.+?)\s+timer""")
    )
    private val japanesePattern =
        Regex("""(.+?)\s*(?:の)?タイマー\s*(?:を)?\s*(?:止めて|キャンセル|やめて)""")

    /** Tokens that mean "every timer" — let [CancelAllTimersMatcher] handle them. */
    private val allKeywords = setOf("all", "every", "全部", "全て", "ぜんぶ", "すべて")

    override fun tryMatch(normalized: String): FastPathMatch? {
        val captured = captureLabel(normalized) ?: return null
        val labelLower = captured.lowercase().trim()
        if (labelLower.isEmpty()) return null
        if (allKeywords.any { labelLower.contains(it) }) return null

        val active = snapshotTimers()
        if (active.isEmpty()) return null

        val match = active.firstOrNull { info ->
            if (info.label.isBlank()) return@firstOrNull false
            val infoLabel = info.label.lowercase()
            infoLabel.contains(labelLower) || labelLower.contains(infoLabel)
        } ?: return null

        return FastPathMatch(
            toolName = "cancel_timer",
            arguments = mapOf("timer_id" to match.id),
            spokenConfirmation = "Cancelled the ${match.label} timer."
        )
    }

    private fun captureLabel(normalized: String): String? {
        for (p in englishPatterns) {
            p.find(normalized)?.let { return it.groupValues[1] }
        }
        japanesePattern.find(normalized)?.let { return it.groupValues[1] }
        return null
    }

    private fun snapshotTimers(): List<TimerInfo> = runBlocking {
        timerManager.getActiveTimers()
    }
}
