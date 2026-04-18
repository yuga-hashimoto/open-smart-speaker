package com.opendash.app.tool.analytics

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ToolUsageStatsTest {

    @Test
    fun `empty stats returns empty snapshot`() {
        val stats = ToolUsageStats()
        assertThat(stats.snapshot()).isEmpty()
        assertThat(stats.totalInvocations()).isEqualTo(0L)
    }

    @Test
    fun `record counts total and success`() {
        val stats = ToolUsageStats()
        stats.record("get_weather", success = true)
        stats.record("get_weather", success = true)
        stats.record("get_weather", success = false)

        val snap = stats.snapshot()
        assertThat(snap).hasSize(1)
        val entry = snap[0]
        assertThat(entry.totalCalls).isEqualTo(3)
        assertThat(entry.successCalls).isEqualTo(2)
        assertThat(entry.failureCalls).isEqualTo(1)
        assertThat(entry.successRate).isWithin(0.01).of(0.666)
    }

    @Test
    fun `snapshot sorted by total calls desc`() {
        val stats = ToolUsageStats()
        repeat(5) { stats.record("a", success = true) }
        repeat(10) { stats.record("b", success = true) }
        repeat(2) { stats.record("c", success = true) }

        val snap = stats.snapshot()
        assertThat(snap.map { it.toolName }).containsExactly("b", "a", "c").inOrder()
    }

    @Test
    fun `totalInvocations sums all`() {
        val stats = ToolUsageStats()
        stats.record("x", true)
        stats.record("y", false)
        stats.record("y", true)

        assertThat(stats.totalInvocations()).isEqualTo(3L)
    }

    @Test
    fun `reset clears all entries`() {
        val stats = ToolUsageStats()
        stats.record("x", true)
        stats.reset()
        assertThat(stats.snapshot()).isEmpty()
    }
}
