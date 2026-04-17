package com.opensmarthome.speaker.ui.settings.analytics

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.data.db.ToolUsageDao
import com.opensmarthome.speaker.data.db.ToolUsageEntity
import com.opensmarthome.speaker.tool.analytics.AnalyticsRepository
import com.opensmarthome.speaker.tool.analytics.PersistentToolUsageStats
import com.opensmarthome.speaker.voice.metrics.LatencyRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Verifies the Settings "Clear tool usage stats" action zeroes the
 * persistent tool_usage table and drops the corresponding rows from the
 * surfaced UI state snapshot. Mirrors the PR #290 multi-room counters
 * clear pattern: only the per-tool table is wiped — latency metrics are
 * left untouched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelClearToolUsageStatsTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Hand-rolled in-memory fake DAO. Avoids mockk on the DAO so the
     * assertions track state naturally after `clear()` is invoked.
     */
    private class FakeToolUsageDao : ToolUsageDao {
        private val rows = mutableMapOf<String, ToolUsageEntity>()

        fun seed(entities: List<ToolUsageEntity>) {
            entities.forEach { rows[it.toolName] = it }
        }

        override suspend fun upsert(entity: ToolUsageEntity) {
            rows[entity.toolName] = entity
        }

        override suspend fun get(name: String): ToolUsageEntity? = rows[name]

        override suspend fun listAll(): List<ToolUsageEntity> =
            rows.values.sortedByDescending { it.totalCalls }.toList()

        override suspend fun clear() {
            rows.clear()
        }
    }

    @Test
    fun `clearToolUsageStats empties persistent tool usage rows and snapshot`() = runTest {
        val dao = FakeToolUsageDao()
        dao.seed(
            listOf(
                ToolUsageEntity("get_weather", 10, 8, 2, 1_000L),
                ToolUsageEntity("set_timer", 4, 4, 0, 2_000L)
            )
        )
        val stats = PersistentToolUsageStats(dao, CoroutineScope(testDispatcher))
        val repository = AnalyticsRepository(dao, stats)
        val latencyRecorder = LatencyRecorder()
        val viewModel = AnalyticsViewModel(repository, latencyRecorder)
        advanceUntilIdle()

        // Pre-condition: VM state reflects seeded rows.
        assertThat(viewModel.state.value.allTime).hasSize(2)
        assertThat(viewModel.state.value.summary?.totalInvocations).isEqualTo(14)

        viewModel.clearToolUsageStats()
        advanceUntilIdle()

        // Post-condition: DAO is empty and VM state collapses to zero rows.
        assertThat(dao.listAll()).isEmpty()
        assertThat(viewModel.state.value.allTime).isEmpty()
        assertThat(viewModel.state.value.summary?.totalInvocations).isEqualTo(0)
    }
}
