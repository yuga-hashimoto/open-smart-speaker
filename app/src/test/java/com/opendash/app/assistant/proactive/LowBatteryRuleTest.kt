package com.opendash.app.assistant.proactive

import com.google.common.truth.Truth.assertThat
import com.opendash.app.util.BatteryStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LowBatteryRuleTest {

    private val ctx = ProactiveContext(
        nowMs = 1_700_000_000_000L,
        hourOfDay = 12,
        dayOfWeek = java.util.Calendar.WEDNESDAY,
    )

    private fun rule(status: BatteryStatus) =
        LowBatteryRule(statusSupplier = { status })

    @Test
    fun `no suggestion while charging even at low level`() = runTest {
        val s = rule(BatteryStatus(level = 5, isCharging = true)).evaluate(ctx)
        assertThat(s).isNull()
    }

    @Test
    fun `no suggestion above LOW_THRESHOLD`() = runTest {
        val s = rule(BatteryStatus(level = 35, isCharging = false)).evaluate(ctx)
        assertThat(s).isNull()
    }

    @Test
    fun `at LOW_THRESHOLD emits NORMAL suggestion`() = runTest {
        val s = rule(BatteryStatus(level = 20, isCharging = false)).evaluate(ctx)
        assertThat(s).isNotNull()
        assertThat(s!!.priority).isEqualTo(Suggestion.Priority.NORMAL)
        assertThat(s.id).isEqualTo("low_battery_low")
        assertThat(s.message).contains("20%")
    }

    @Test
    fun `between CRITICAL and LOW thresholds emits NORMAL suggestion`() = runTest {
        val s = rule(BatteryStatus(level = 15, isCharging = false)).evaluate(ctx)
        assertThat(s).isNotNull()
        assertThat(s!!.priority).isEqualTo(Suggestion.Priority.NORMAL)
    }

    @Test
    fun `at CRITICAL_THRESHOLD emits HIGH priority suggestion`() = runTest {
        val s = rule(BatteryStatus(level = 10, isCharging = false)).evaluate(ctx)
        assertThat(s).isNotNull()
        assertThat(s!!.priority).isEqualTo(Suggestion.Priority.HIGH)
        assertThat(s.id).isEqualTo("low_battery_critical")
    }

    @Test
    fun `below CRITICAL_THRESHOLD still emits HIGH priority suggestion`() = runTest {
        val s = rule(BatteryStatus(level = 3, isCharging = false)).evaluate(ctx)
        assertThat(s).isNotNull()
        assertThat(s!!.priority).isEqualTo(Suggestion.Priority.HIGH)
    }

    @Test
    fun `negative level returns null (guard for uninitialised BatteryMonitor)`() = runTest {
        val s = rule(BatteryStatus(level = -1, isCharging = false)).evaluate(ctx)
        assertThat(s).isNull()
    }

    @Test
    fun `suggestion has expiry in the future so SuggestionState can refresh`() = runTest {
        val s = rule(BatteryStatus(level = 10, isCharging = false)).evaluate(ctx)
        assertThat(s!!.expiresAtMs).isNotNull()
        assertThat(s.expiresAtMs!!).isGreaterThan(ctx.nowMs)
    }
}
