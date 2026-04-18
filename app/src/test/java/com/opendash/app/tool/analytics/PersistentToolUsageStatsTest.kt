package com.opendash.app.tool.analytics

import com.google.common.truth.Truth.assertThat
import com.opendash.app.data.db.ToolUsageDao
import com.opendash.app.data.db.ToolUsageEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersistentToolUsageStatsTest {

    @Test
    fun `first record inserts new entity`() = runTest {
        val dao: ToolUsageDao = mockk(relaxed = true)
        coEvery { dao.get("get_weather") } returns null
        val captured = slot<ToolUsageEntity>()
        coEvery { dao.upsert(capture(captured)) } returns Unit

        val dispatcher = StandardTestDispatcher(testScheduler)
        val stats = PersistentToolUsageStats(dao, CoroutineScope(dispatcher))

        stats.record("get_weather", success = true)
        runCurrent()

        assertThat(captured.isCaptured).isTrue()
        assertThat(captured.captured.toolName).isEqualTo("get_weather")
        assertThat(captured.captured.totalCalls).isEqualTo(1)
        assertThat(captured.captured.successCalls).isEqualTo(1)
    }

    @Test
    fun `subsequent record increments counters`() = runTest {
        val dao: ToolUsageDao = mockk(relaxed = true)
        coEvery { dao.get("x") } returns ToolUsageEntity("x", 5, 3, 2, 0)
        val captured = slot<ToolUsageEntity>()
        coEvery { dao.upsert(capture(captured)) } returns Unit

        val dispatcher = StandardTestDispatcher(testScheduler)
        val stats = PersistentToolUsageStats(dao, CoroutineScope(dispatcher))

        stats.record("x", success = false)
        runCurrent()

        assertThat(captured.captured.totalCalls).isEqualTo(6)
        assertThat(captured.captured.successCalls).isEqualTo(3)
        assertThat(captured.captured.failureCalls).isEqualTo(3)
    }

    @Test
    fun `snapshotSession returns in-memory entries`() = runTest {
        val dao: ToolUsageDao = mockk(relaxed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stats = PersistentToolUsageStats(dao, CoroutineScope(dispatcher))

        stats.record("a", true)
        stats.record("a", true)
        stats.record("b", false)
        runCurrent()

        val snap = stats.snapshotSession()
        assertThat(snap.first().toolName).isEqualTo("a")
        assertThat(snap.first().totalCalls).isEqualTo(2)
    }

    @Test
    fun `resetAll clears dao and memory`() = runTest {
        val dao: ToolUsageDao = mockk(relaxed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stats = PersistentToolUsageStats(dao, CoroutineScope(dispatcher))
        stats.record("x", true)
        runCurrent()

        stats.resetAll()
        coVerify { dao.clear() }
        assertThat(stats.snapshotSession()).isEmpty()
    }
}
