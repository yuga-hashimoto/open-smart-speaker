package com.opendash.app.util

import android.content.Intent
import android.os.BatteryManager
import com.google.common.truth.Truth.assertThat
import com.opendash.app.util.BatteryMonitor.Companion.toBatteryStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

/**
 * Verifies the BatteryStatus decoding logic independently of the sticky-intent
 * registration plumbing. We don't instantiate BatteryMonitor itself — that
 * requires a real Context + BroadcastReceiver, which belongs in instrumented
 * tests. The pure function under test is the Intent → BatteryStatus mapping.
 */
class BatteryMonitorTest {

    private fun intent(level: Int, scale: Int, status: Int, plugged: Int): Intent {
        val intent = mockk<Intent>()
        every { intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns level
        every { intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns scale
        every { intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN) } returns status
        every { intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) } returns plugged
        return intent
    }

    @Test
    fun `full discharging battery computes 100 percent not charging`() {
        val s = intent(100, 100, BatteryManager.BATTERY_STATUS_DISCHARGING, 0).toBatteryStatus()
        assertThat(s.level).isEqualTo(100)
        assertThat(s.isCharging).isFalse()
        assertThat(s.isLow).isFalse()
    }

    @Test
    fun `low battery unplugged is low`() {
        val s = intent(15, 100, BatteryManager.BATTERY_STATUS_DISCHARGING, 0).toBatteryStatus()
        assertThat(s.level).isEqualTo(15)
        assertThat(s.isCharging).isFalse()
        assertThat(s.isLow).isTrue()
    }

    @Test
    fun `low battery plugged is not low`() {
        val s = intent(15, 100, BatteryManager.BATTERY_STATUS_CHARGING, 1).toBatteryStatus()
        assertThat(s.isCharging).isTrue()
        assertThat(s.isLow).isFalse()
    }

    @Test
    fun `battery status full is considered charging`() {
        val s = intent(100, 100, BatteryManager.BATTERY_STATUS_FULL, 0).toBatteryStatus()
        assertThat(s.isCharging).isTrue()
    }

    @Test
    fun `threshold boundary is inclusive at 20 percent`() {
        val s = intent(20, 100, BatteryManager.BATTERY_STATUS_DISCHARGING, 0).toBatteryStatus()
        assertThat(s.isLow).isTrue()
    }

    @Test
    fun `threshold boundary just above 20 percent is not low`() {
        val s = intent(21, 100, BatteryManager.BATTERY_STATUS_DISCHARGING, 0).toBatteryStatus()
        assertThat(s.isLow).isFalse()
    }

    @Test
    fun `scaled level is normalized to percentage`() {
        val s = intent(50, 200, BatteryManager.BATTERY_STATUS_DISCHARGING, 0).toBatteryStatus()
        assertThat(s.level).isEqualTo(25)
        assertThat(s.isLow).isFalse()
    }
}
