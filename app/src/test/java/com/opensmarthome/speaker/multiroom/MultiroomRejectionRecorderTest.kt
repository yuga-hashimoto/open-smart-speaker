package com.opensmarthome.speaker.multiroom

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.data.db.MultiroomRejectionDao
import com.opensmarthome.speaker.data.db.MultiroomRejectionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The production DAO uses Room's `INSERT ... ON CONFLICT(reason) DO
 * UPDATE` which isn't available in plain JVM tests without Robolectric.
 * We exercise the recorder against a hand-written fake DAO that
 * preserves the PK-on-reason upsert semantics — enough to cover the
 * recorder's contract (atomic increment, last-write-wins on lastAtMs).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MultiroomRejectionRecorderTest {

    private class FakeMultiroomRejectionDao : MultiroomRejectionDao {
        private val rows = MutableStateFlow<List<MultiroomRejectionEntity>>(emptyList())
        private val mutex = Mutex()

        override suspend fun upsertIncrement(reason: String, nowMs: Long) {
            mutex.withLock {
                val current = rows.value
                val existing = current.firstOrNull { it.reason == reason }
                val updated = if (existing == null) {
                    current + MultiroomRejectionEntity(reason = reason, count = 1, lastAtMs = nowMs)
                } else {
                    current.map {
                        if (it.reason == reason) it.copy(count = it.count + 1, lastAtMs = nowMs)
                        else it
                    }
                }
                rows.value = updated.sortedBy { it.reason }
            }
        }

        override fun observe(): Flow<List<MultiroomRejectionEntity>> = rows

        override suspend fun listAll(): List<MultiroomRejectionEntity> = rows.value

        override suspend fun clear() {
            rows.value = emptyList()
        }
    }

    @Test
    fun `records two HMAC_MISMATCH and one REPLAY_WINDOW with correct counts`() = runTest {
        val dao = FakeMultiroomRejectionDao()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val recorder = MultiroomRejectionRecorder(dao = dao, scope = CoroutineScope(dispatcher))

        recorder.record(reason = "HMAC_MISMATCH", nowMs = 100L)
        recorder.record(reason = "HMAC_MISMATCH", nowMs = 200L)
        recorder.record(reason = "REPLAY_WINDOW", nowMs = 300L)
        runCurrent()

        val observed = dao.observe().first()
        assertThat(observed).hasSize(2)

        val hmac = observed.first { it.reason == "HMAC_MISMATCH" }
        assertThat(hmac.count).isEqualTo(2L)
        assertThat(hmac.lastAtMs).isEqualTo(200L)

        val replay = observed.first { it.reason == "REPLAY_WINDOW" }
        assertThat(replay.count).isEqualTo(1L)
        assertThat(replay.lastAtMs).isEqualTo(300L)
    }

    @Test
    fun `recorder swallows DAO failures silently without throwing`() = runTest {
        val failing = object : MultiroomRejectionDao {
            override suspend fun upsertIncrement(reason: String, nowMs: Long) {
                error("db offline")
            }
            override fun observe(): Flow<List<MultiroomRejectionEntity>> = MutableStateFlow(emptyList())
            override suspend fun listAll(): List<MultiroomRejectionEntity> = emptyList()
            override suspend fun clear() = Unit
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val recorder = MultiroomRejectionRecorder(dao = failing, scope = CoroutineScope(dispatcher))

        // If the fire-and-forget swallow is broken, runCurrent propagates
        // the error out of the scope and the test fails. No assertion
        // needed: the absence of an exception is the assertion.
        recorder.record(reason = "HMAC_MISMATCH", nowMs = 1L)
        runCurrent()
    }
}
