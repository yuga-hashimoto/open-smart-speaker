package com.opensmarthome.speaker.multiroom

import com.opensmarthome.speaker.data.db.MultiroomTrafficDao
import com.opensmarthome.speaker.data.db.MultiroomTrafficEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lifetime-counter recorder for multi-room envelope traffic. Wraps
 * [MultiroomTrafficDao] so the hot path (dispatcher inbound,
 * broadcaster outbound) can fire-and-forget without awaiting a DB
 * write.
 *
 * Pattern mirrors [com.opensmarthome.speaker.tool.analytics.PersistentToolUsageStats] —
 * synchronous entry point launches onto an IO scope so the receive
 * loop / fan-out doesn't pay the SQLite latency.
 */
@Singleton
class MultiroomTrafficRecorder internal constructor(
    private val dao: MultiroomTrafficDao,
    private val scope: CoroutineScope
) {
    /**
     * Production constructor — Hilt invokes this one. We own the scope
     * internally because there's no app-wide `CoroutineScope` binding
     * to inject, and one private per-recorder scope matches the
     * [PersistentToolUsageStats] pattern for the same reason.
     */
    @Inject constructor(dao: MultiroomTrafficDao) : this(
        dao = dao,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    /**
     * Record that an authenticated inbound envelope of [type] was
     * received at [nowMs]. Non-blocking — persist failures are logged
     * but never thrown so a flaky DB can't crash the dispatch loop.
     */
    fun recordInbound(type: String, nowMs: Long = System.currentTimeMillis()) {
        persist(type = type, direction = MultiroomTrafficEntity.DIRECTION_IN, nowMs = nowMs)
    }

    /**
     * Record that an outbound envelope of [type] was successfully
     * delivered to a peer at [nowMs]. Called once per [SendOutcome.Ok]
     * — failures deliberately don't count so a flapping network
     * doesn't inflate the "sent" total.
     */
    fun recordOutbound(type: String, nowMs: Long = System.currentTimeMillis()) {
        persist(type = type, direction = MultiroomTrafficEntity.DIRECTION_OUT, nowMs = nowMs)
    }

    private fun persist(type: String, direction: String, nowMs: Long) {
        scope.launch {
            runCatching { dao.upsertIncrement(type = type, direction = direction, nowMs = nowMs) }
                .onFailure { Timber.w(it, "Failed to persist multiroom traffic $direction/$type") }
        }
    }
}
