package com.opensmarthome.speaker.voice.fastpath

import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.jupiter.api.Test

class AlarmTimeCalculatorTest {

    @Test
    fun `seconds until future same day target`() {
        val now = LocalDateTime.of(2026, 4, 17, 6, 0, 0) // 06:00:00
        val seconds = AlarmTimeCalculator.secondsUntil(now, 7, 0)
        // 1h = 3600s
        assertThat(seconds).isEqualTo(3600)
    }

    @Test
    fun `seconds until future same day with minutes`() {
        val now = LocalDateTime.of(2026, 4, 17, 5, 45, 0)
        val seconds = AlarmTimeCalculator.secondsUntil(now, 6, 30)
        // 45m = 2700s
        assertThat(seconds).isEqualTo(2700)
    }

    @Test
    fun `past time rolls to tomorrow`() {
        val now = LocalDateTime.of(2026, 4, 17, 8, 0, 0)
        val seconds = AlarmTimeCalculator.secondsUntil(now, 7, 0)
        // target is 1h ago → rolls 23h ahead
        assertThat(seconds).isEqualTo(23 * 3600)
    }

    @Test
    fun `target equal to now rolls to tomorrow`() {
        val now = LocalDateTime.of(2026, 4, 17, 9, 15, 0)
        val seconds = AlarmTimeCalculator.secondsUntil(now, 9, 15)
        assertThat(seconds).isEqualTo(24 * 3600)
    }

    @Test
    fun `normalize hour handles am 12 as midnight`() {
        assertThat(AlarmTimeCalculator.normalizeHour(12, "am")).isEqualTo(0)
    }

    @Test
    fun `normalize hour handles pm 12 as noon`() {
        assertThat(AlarmTimeCalculator.normalizeHour(12, "pm")).isEqualTo(12)
    }

    @Test
    fun `normalize hour handles pm 7 as 19`() {
        assertThat(AlarmTimeCalculator.normalizeHour(7, "pm")).isEqualTo(19)
    }

    @Test
    fun `normalize hour without suffix passes through 24h`() {
        assertThat(AlarmTimeCalculator.normalizeHour(19, null)).isEqualTo(19)
    }

    @Test
    fun `normalize hour rejects out of range`() {
        assertThat(AlarmTimeCalculator.normalizeHour(25, null)).isNull()
        assertThat(AlarmTimeCalculator.normalizeHour(0, "am")).isNull()
        assertThat(AlarmTimeCalculator.normalizeHour(13, "pm")).isNull()
    }
}

class AlarmMatcherTest {

    // Fixed "now" = 2026-04-17 05:00 so "7am", "6:30" are future same-day.
    private val fixedNow = LocalDateTime.of(2026, 4, 17, 5, 0, 0)
    private val matcher = AlarmMatcherImpl(nowProvider = { fixedNow })
    private val router = DefaultFastPathRouter(
        matchers = listOf(TimerMatcher, matcher) + DefaultFastPathRouter.DEFAULT_MATCHERS
    )

    @Test
    fun `set alarm for 7am dispatches set_timer with remaining seconds`() {
        val m = matcher.tryMatch("set an alarm for 7am")
        assertThat(m).isNotNull()
        assertThat(m!!.toolName).isEqualTo("set_timer")
        // 05:00 -> 07:00 = 7200s
        assertThat(m.arguments["seconds"]).isEqualTo(7200.0)
        assertThat(m.spokenConfirmation).contains("7")
        assertThat(m.spokenConfirmation).contains("am")
    }

    @Test
    fun `set alarm for 7 30 am with minutes`() {
        val m = matcher.tryMatch("set an alarm for 7:30 am")
        // 05:00 -> 07:30 = 9000s
        assertThat(m?.arguments?.get("seconds")).isEqualTo(9000.0)
    }

    @Test
    fun `set alarm for 9pm pm conversion`() {
        val m = matcher.tryMatch("set an alarm for 9pm")
        // 05:00 -> 21:00 = 16h = 57600s
        assertThat(m?.arguments?.get("seconds")).isEqualTo(57600.0)
    }

    @Test
    fun `wake me up at 6 30 no suffix`() {
        val m = matcher.tryMatch("wake me up at 6:30")
        // 05:00 -> 06:30 = 5400s
        assertThat(m?.toolName).isEqualTo("set_timer")
        assertThat(m?.arguments?.get("seconds")).isEqualTo(5400.0)
    }

    @Test
    fun `wake me up at 7am`() {
        val m = matcher.tryMatch("wake me up at 7am")
        assertThat(m?.arguments?.get("seconds")).isEqualTo(7200.0)
    }

    @Test
    fun `japanese 7 oclock alarm hour only`() {
        val m = matcher.tryMatch("7時にアラーム")
        // 05:00 -> 07:00 = 7200s
        assertThat(m?.toolName).isEqualTo("set_timer")
        assertThat(m?.arguments?.get("seconds")).isEqualTo(7200.0)
        assertThat(m?.spokenConfirmation).contains("7時")
        assertThat(m?.spokenConfirmation).contains("アラーム")
    }

    @Test
    fun `japanese 6 30 alarm with minutes`() {
        val m = matcher.tryMatch("6時30分に目覚まし")
        assertThat(m?.arguments?.get("seconds")).isEqualTo(5400.0)
        assertThat(m?.spokenConfirmation).contains("6時30分")
    }

    @Test
    fun `japanese wake me up form`() {
        val m = matcher.tryMatch("7時に起こして")
        assertThat(m?.arguments?.get("seconds")).isEqualTo(7200.0)
    }

    @Test
    fun `past time rolls to tomorrow for alarm`() {
        val lateNow = LocalDateTime.of(2026, 4, 17, 22, 0, 0)
        val m = AlarmMatcherImpl(nowProvider = { lateNow }).tryMatch("set an alarm for 7am")
        // 22:00 -> 07:00 next day = 9h = 32400s
        assertThat(m?.arguments?.get("seconds")).isEqualTo(32400.0)
    }

    @Test
    fun `set a timer does not match AlarmMatcher`() {
        // AlarmMatcher alone on "set a timer for 5 minutes" must return null.
        val m = matcher.tryMatch("set a timer for 5 minutes")
        assertThat(m).isNull()
    }

    @Test
    fun `router preserves TimerMatcher dominance over AlarmMatcher`() {
        // Via full router, "set a timer for 5 minutes" should hit TimerMatcher,
        // not AlarmMatcher.
        val m = router.match("set a timer for 5 minutes")
        assertThat(m?.toolName).isEqualTo("set_timer")
        // 5 minutes = 300s (timer), NOT a wall-clock interpretation.
        assertThat(m?.arguments?.get("seconds")).isEqualTo(300.0)
    }

    @Test
    fun `default router matches set an alarm for 7am`() {
        // Sanity — the default router (real LocalDateTime.now) dispatches set_timer
        // for an alarm utterance. We only check toolName to avoid time-coupling.
        val m = DefaultFastPathRouter().match("set an alarm for 7am")
        assertThat(m?.toolName).isEqualTo("set_timer")
    }
}
