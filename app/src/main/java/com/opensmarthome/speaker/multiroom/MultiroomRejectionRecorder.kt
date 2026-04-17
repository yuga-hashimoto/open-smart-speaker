package com.opensmarthome.speaker.multiroom

import com.opensmarthome.speaker.data.db.MultiroomRejectionDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lifetime-counter recorder for multi-room envelope rejections, keyed
 * by [AnnouncementParser.Reason]. Wraps [MultiroomRejectionDao] so the
 * receive loop can fire-and-forget without awaiting a DB write.
 *
 * Pattern mirrors [MultiroomTrafficRecorder] — rejections live in
 * their own table because they track a fundamentally different signal
 * (untrusted/malformed traffic) from authenticated inbound/outbound
 * counts.
 */
@Singleton
class MultiroomRejectionRecorder internal constructor(
    private val dao: MultiroomRejectionDao,
    private val scope: CoroutineScope
) {
    /**
     * Production constructor — Hilt invokes this one. Owns its own IO
     * scope for the same reason as [MultiroomTrafficRecorder].
     */
    @Inject constructor(dao: MultiroomRejectionDao) : this(
        dao = dao,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    /**
     * Record that an envelope was rejected with [reason] at [nowMs].
     * Non-blocking — persist failures are logged but never thrown so a
     * flaky DB can't crash the dispatch loop.
     */
    fun record(reason: String, nowMs: Long = System.currentTimeMillis()) {
        scope.launch {
            runCatching { dao.upsertIncrement(reason = reason, nowMs = nowMs) }
                .onFailure { Timber.w(it, "Failed to persist multiroom rejection $reason") }
        }
    }
}
