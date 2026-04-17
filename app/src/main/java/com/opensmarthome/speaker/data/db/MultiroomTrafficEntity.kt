package com.opensmarthome.speaker.data.db

import androidx.room.Entity

/**
 * Lifetime counter for multi-room envelope traffic, keyed by the
 * envelope [type] (e.g. `tts_broadcast`, `heartbeat`) and [direction]
 * (`"in"` for received envelopes, `"out"` for successfully-sent ones).
 *
 * One row per (type, direction) pair. Updated via
 * [MultiroomTrafficDao.upsertIncrement] every time the corresponding
 * authenticated envelope is observed on the wire.
 *
 * Surfaced in the System Info screen so the user can confirm the mesh
 * is actually exchanging traffic ("5 tts_broadcasts sent, 12 heartbeats
 * received").
 */
@Entity(tableName = "multiroom_traffic", primaryKeys = ["type", "direction"])
data class MultiroomTrafficEntity(
    val type: String,
    val direction: String,
    val count: Long,
    val lastAtMs: Long
) {
    companion object {
        /** Direction value for envelopes received from peers. */
        const val DIRECTION_IN = "in"

        /** Direction value for envelopes sent to peers (Ok outcomes only). */
        const val DIRECTION_OUT = "out"
    }
}
