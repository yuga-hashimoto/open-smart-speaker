package com.opensmarthome.speaker.ui.home

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ClockWidgetFormatTest {

    @Test
    fun `24h mode zero-pads single digit hours`() {
        val t = LocalDateTime.of(2026, 4, 17, 9, 5)
        assertThat(formatHourMinute(t, use24Hour = true)).isEqualTo("09:05")
    }

    @Test
    fun `24h mode renders evening hours as 13-23`() {
        val t = LocalDateTime.of(2026, 4, 17, 19, 30)
        assertThat(formatHourMinute(t, use24Hour = true)).isEqualTo("19:30")
    }

    @Test
    fun `12h mode drops leading zero from hour`() {
        val t = LocalDateTime.of(2026, 4, 17, 9, 5)
        assertThat(formatHourMinute(t, use24Hour = false)).isEqualTo("9:05")
    }

    @Test
    fun `12h mode wraps evening hours back to 1-12`() {
        val t = LocalDateTime.of(2026, 4, 17, 19, 30)
        assertThat(formatHourMinute(t, use24Hour = false)).isEqualTo("7:30")
    }

    @Test
    fun `12h mode uses 12 for noon and midnight`() {
        val noon = LocalDateTime.of(2026, 4, 17, 12, 0)
        val midnight = LocalDateTime.of(2026, 4, 17, 0, 0)
        assertThat(formatHourMinute(noon, use24Hour = false)).isEqualTo("12:00")
        assertThat(formatHourMinute(midnight, use24Hour = false)).isEqualTo("12:00")
    }
}
