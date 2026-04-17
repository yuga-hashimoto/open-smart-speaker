package com.opensmarthome.speaker.multiroom

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.data.db.MultiroomTrafficDao
import com.opensmarthome.speaker.data.db.MultiroomTrafficEntity
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
 * Room isn't available in JVM unit tests here (no Robolectric on the
 * classpath) so we exercise the recorder against a hand-written fake
 * DAO that preserves the composite-PK semantics of the real @Query
 * (INSERT ON CONFLICT UPDATE). That's the contract the recorder
 * depends on — if the fake enforces "one row per (type, direction)
 * and increment on collision", the recorder's call pattern is fully
 * covered without spinning up a SQLite engine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MultiroomTrafficRecorderTest {

    private class FakeMultiroomTrafficDao : MultiroomTrafficDao {
        private val rows = MutableStateFlow<List<MultiroomTrafficEntity>>(emptyList())
        private val mutex = Mutex()

        override suspend fun upsertIncrement(type: String, direction: String, nowMs: Long) {
            mutex.withLock {
                val current = rows.value
                val existing = current.firstOrNull { it.type == type && it.direction == direction }
                val updated = if (existing == null) {
                    current + MultiroomTrafficEntity(type = type, direction = direction, count = 1, lastAtMs = nowMs)
                } else {
                    current.map {
                        if (it.type == type && it.direction == direction)
                            it.copy(count = it.count + 1, lastAtMs = nowMs)
                        else it
                    }
                }
                rows.value = sortedRows(updated)
            }
        }

        override fun observe(): Flow<List<MultiroomTrafficEntity>> = rows

        override suspend fun listAll(): List<MultiroomTrafficEntity> = rows.value

        override suspend fun clear() {
            rows.value = emptyList()
        }

        private fun sortedRows(list: List<MultiroomTrafficEntity>): List<MultiroomTrafficEntity> =
            list.sortedWith(compareBy({ it.type }, { it.direction }))
    }

    @Test
    fun `records two inbound tts_broadcast and one outbound heartbeat with correct counts`() = runTest {
        val dao = FakeMultiroomTrafficDao()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val recorder = MultiroomTrafficRecorder(dao = dao, scope = CoroutineScope(dispatcher))

        recorder.recordInbound(type = "tts_broadcast", nowMs = 100L)
        recorder.recordInbound(type = "tts_broadcast", nowMs = 200L)
        recorder.recordOutbound(type = "heartbeat", nowMs = 300L)
        runCurrent()

        val observed = dao.observe().first()
        assertThat(observed).hasSize(2)

        val inbound = observed.first { it.type == "tts_broadcast" && it.direction == MultiroomTrafficEntity.DIRECTION_IN }
        assertThat(inbound.count).isEqualTo(2L)
        assertThat(inbound.lastAtMs).isEqualTo(200L)

        val outbound = observed.first { it.type == "heartbeat" && it.direction == MultiroomTrafficEntity.DIRECTION_OUT }
        assertThat(outbound.count).isEqualTo(1L)
        assertThat(outbound.lastAtMs).isEqualTo(300L)
    }

    @Test
    fun `inbound and outbound of same type are tracked independently`() = runTest {
        val dao = FakeMultiroomTrafficDao()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val recorder = MultiroomTrafficRecorder(dao = dao, scope = CoroutineScope(dispatcher))

        // Same envelope type, opposite directions must not collapse into one row.
        recorder.recordInbound(type = "tts_broadcast", nowMs = 10L)
        recorder.recordOutbound(type = "tts_broadcast", nowMs = 20L)
        recorder.recordOutbound(type = "tts_broadcast", nowMs = 30L)
        runCurrent()

        val rows = dao.listAll()
        assertThat(rows).hasSize(2)
        assertThat(rows.first { it.direction == MultiroomTrafficEntity.DIRECTION_IN }.count).isEqualTo(1L)
        assertThat(rows.first { it.direction == MultiroomTrafficEntity.DIRECTION_OUT }.count).isEqualTo(2L)
    }

    @Test
    fun `recorder swallows DAO failures silently without throwing`() = runTest {
        val failing = object : MultiroomTrafficDao {
            override suspend fun upsertIncrement(type: String, direction: String, nowMs: Long) {
                error("db offline")
            }
            override fun observe(): Flow<List<MultiroomTrafficEntity>> = MutableStateFlow(emptyList())
            override suspend fun listAll(): List<MultiroomTrafficEntity> = emptyList()
            override suspend fun clear() = Unit
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val recorder = MultiroomTrafficRecorder(dao = failing, scope = CoroutineScope(dispatcher))

        // If the fire-and-forget swallow is broken, runCurrent propagates
        // the error out of the scope and the test fails. No assertion
        // needed: the absence of an exception is the assertion.
        recorder.recordInbound(type = "heartbeat", nowMs = 1L)
        recorder.recordOutbound(type = "heartbeat", nowMs = 2L)
        runCurrent()
    }
}
