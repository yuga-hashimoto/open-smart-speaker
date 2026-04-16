package com.opensmarthome.speaker.tool.analytics

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.data.db.ToolUsageDao
import com.opensmarthome.speaker.data.db.ToolUsageEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AnalyticsRepositoryTest {

    @Test
    fun `summary aggregates counters`() = runTest {
        val dao: ToolUsageDao = mockk()
        coEvery { dao.listAll() } returns listOf(
            ToolUsageEntity("a", 10, 8, 2, 100L),
            ToolUsageEntity("b", 20, 15, 5, 200L),
            ToolUsageEntity("c", 5, 5, 0, 300L)
        )

        val stats: PersistentToolUsageStats = mockk(relaxed = true)
        val repo = AnalyticsRepository(dao, stats)

        val summary = repo.summary()
        assertThat(summary.totalInvocations).isEqualTo(35)
        assertThat(summary.totalSuccesses).isEqualTo(28)
        assertThat(summary.totalFailures).isEqualTo(7)
        assertThat(summary.uniqueTools).isEqualTo(3)
        assertThat(summary.globalSuccessRate).isWithin(0.001).of(28.0 / 35.0)
    }

    @Test
    fun `summary with no data returns zero rate`() = runTest {
        val dao: ToolUsageDao = mockk()
        coEvery { dao.listAll() } returns emptyList()
        val stats: PersistentToolUsageStats = mockk(relaxed = true)
        val repo = AnalyticsRepository(dao, stats)

        val s = repo.summary()
        assertThat(s.totalInvocations).isEqualTo(0)
        assertThat(s.globalSuccessRate).isEqualTo(0.0)
    }

    @Test
    fun `topN respects limit`() = runTest {
        val dao: ToolUsageDao = mockk()
        coEvery { dao.listAll() } returns (1..5).map {
            ToolUsageEntity("tool$it", it.toLong(), it.toLong(), 0, 0L)
        }
        val stats: PersistentToolUsageStats = mockk(relaxed = true)
        val repo = AnalyticsRepository(dao, stats)

        assertThat(repo.topN(2)).hasSize(2)
        assertThat(repo.topN(100)).hasSize(5)
    }
}
