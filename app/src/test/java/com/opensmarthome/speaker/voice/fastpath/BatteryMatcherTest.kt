package com.opensmarthome.speaker.voice.fastpath

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.util.BatteryMonitor
import com.opensmarthome.speaker.util.BatteryStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test

class BatteryMatcherTest {

    private fun matcher(level: Int, isCharging: Boolean): BatteryMatcher {
        val bm = mockk<BatteryMonitor>()
        every { bm.status } returns MutableStateFlow(BatteryStatus(level = level, isCharging = isCharging))
        return BatteryMatcher(bm)
    }

    @Test
    fun `english discharging reports level without charging suffix`() {
        val m = matcher(level = 72, isCharging = false)
        val result = m.tryMatch("what's my battery")
        assertThat(result?.spokenConfirmation).isEqualTo("Battery is at 72 percent.")
    }

    @Test
    fun `english charging adds charging suffix`() {
        val m = matcher(level = 45, isCharging = true)
        val result = m.tryMatch("battery level")
        assertThat(result?.spokenConfirmation).isEqualTo("Battery is at 45 percent and charging.")
    }

    @Test
    fun `japanese discharging uses ja template`() {
        val m = matcher(level = 20, isCharging = false)
        val result = m.tryMatch("バッテリー残量")
        assertThat(result?.spokenConfirmation).isEqualTo("バッテリー残量は20％です。")
    }

    @Test
    fun `japanese charging uses ja charging template`() {
        val m = matcher(level = 80, isCharging = true)
        val result = m.tryMatch("電池残量")
        assertThat(result?.spokenConfirmation).isEqualTo("バッテリー残量は80％、充電中です。")
    }

    @Test
    fun `unrelated utterance returns null`() {
        val m = matcher(level = 100, isCharging = true)
        assertThat(m.tryMatch("set a timer for five minutes")).isNull()
        assertThat(m.tryMatch("turn off the lights")).isNull()
    }

    @Test
    fun `match is speak-only — no tool call dispatched`() {
        val m = matcher(level = 50, isCharging = false)
        val result = m.tryMatch("how much battery do I have left")
        assertThat(result?.toolName).isNull()
    }
}
