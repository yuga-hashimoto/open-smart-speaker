package com.opensmarthome.speaker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Lifetime counter for multi-room envelope rejections, keyed by the
 * [AnnouncementParser.Reason] name (e.g. `MALFORMED_JSON`,
 * `HMAC_MISMATCH`). One row per reason.
 *
 * Distinct from [MultiroomTrafficEntity], which counts *authenticated*
 * inbound/outbound traffic. Rejections are the signal-to-noise ratio
 * metric: if `HMAC_MISMATCH` is high, the shared secret is probably
 * wrong; if `REPLAY_WINDOW` is high, clocks are skewed.
 *
 * Surfaced in the System Info screen under "Multi-room rejections".
 */
@Entity(tableName = "multiroom_rejections")
data class MultiroomRejectionEntity(
    @PrimaryKey val reason: String,
    val count: Long,
    val lastAtMs: Long
)
