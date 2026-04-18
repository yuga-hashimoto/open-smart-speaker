package com.opendash.app.assistant.proactive

import com.google.common.truth.Truth.assertThat
import com.opendash.app.util.BatteryStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChargingCompleteRuleTest {

    private val ctx = ProactiveContext(
        nowMs = 1_700_000_000_000L,
        hourOfDay = 12,
        dayOfWeek = java.util.Calendar.WEDNESDAY,
    )

    private fun rule(status: BatteryStatus) =
        ChargingCompleteRule(statusSupplier = { status })

    @Test
    fun `no suggestion while not charging regardless of level`() = runTest {
        val s = rule(BatteryStatus(level = 100, isCharging = false)).evaluate(ctx)
        assertThat(s).isNull()
    }

    @Test
    fun `no suggestion while charging but under 100`() = runTest {
        val s = rule(BatteryStatus(level = 95, isCharging = true)).evaluate(ctx)
        assertThat(s).isNull()
    }

    @Test
    fun `at 100 percent and charging emits LOW priority suggestion`() = runTest {
        val s = rule(BatteryStatus(level = 100, isCharging = true)).evaluate(ctx)
        assertThat(s).isNotNull()
        assertThat(s!!.priority).isEqualTo(Suggestion.Priority.LOW)
        assertThat(s.id).isEqualTo("charging_complete")
        assertThat(s.message).contains("fully charged")
    }

    @Test
    fun `suggestion carries a future expiry`() = runTest {
        val s = rule(BatteryStatus(level = 100, isCharging = true)).evaluate(ctx)
        assertThat(s!!.expiresAtMs).isNotNull()
        assertThat(s.expiresAtMs!!).isGreaterThan(ctx.nowMs)
    }

    @Test
    fun `suggestedAction is null — it is a nudge, not a one-tap action`() = runTest {
        val s = rule(BatteryStatus(level = 100, isCharging = true)).evaluate(ctx)
        assertThat(s!!.suggestedAction).isNull()
    }
}
