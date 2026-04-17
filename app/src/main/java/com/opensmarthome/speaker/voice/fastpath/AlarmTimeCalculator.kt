package com.opensmarthome.speaker.voice.fastpath

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Pure helper for computing seconds-until a wall-clock target time. Used by
 * [AlarmMatcher] so an utterance like "set an alarm for 7am" can be
 * translated into a `set_timer` call with the remaining seconds.
 *
 * If the target hour/minute is earlier than (or equal to) the current time,
 * the target rolls to the same time tomorrow — classic alarm-clock semantics.
 *
 * Kept separate from [AlarmMatcher] for easy unit testing with a fixed
 * "now" injected.
 */
internal object AlarmTimeCalculator {
    private const val SECONDS_PER_DAY = 24 * 60 * 60

    /**
     * @param now current local time; injectable for tests.
     * @param targetHour 0..23
     * @param targetMinute 0..59
     * @return seconds until the next occurrence of `targetHour:targetMinute`.
     *     Always strictly positive — if the target is "now", rolls to tomorrow.
     */
    fun secondsUntil(now: LocalDateTime, targetHour: Int, targetMinute: Int): Int {
        require(targetHour in 0..23) { "targetHour out of range: $targetHour" }
        require(targetMinute in 0..59) { "targetMinute out of range: $targetMinute" }
        val target = LocalTime.of(targetHour, targetMinute)
        val nowTime = now.toLocalTime()
        val diffSeconds = target.toSecondOfDay() - nowTime.toSecondOfDay()
        return if (diffSeconds > 0) diffSeconds else diffSeconds + SECONDS_PER_DAY
    }

    /**
     * Normalizes `hour` + optional am/pm suffix into a 24h hour.
     * - "am" + 12 → 0 (midnight)
     * - "pm" + 1..11 → 13..23
     * - "pm" + 12 → 12 (noon)
     * - no suffix → returned as-is (assumed already 24h or will be rolled
     *   to the next future occurrence by [secondsUntil])
     *
     * Returns null on invalid input (hour out of range, malformed suffix).
     */
    fun normalizeHour(hour: Int, amPm: String?): Int? {
        val suffix = amPm?.lowercase()?.trim()?.takeIf { it.isNotEmpty() }
        return when (suffix) {
            null -> if (hour in 0..23) hour else null
            "am" -> when (hour) {
                12 -> 0
                in 1..11 -> hour
                else -> null
            }
            "pm" -> when (hour) {
                12 -> 12
                in 1..11 -> hour + 12
                else -> null
            }
            else -> null
        }
    }
}
