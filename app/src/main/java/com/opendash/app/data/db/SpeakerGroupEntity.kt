package com.opendash.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists a named speaker group — a client-side subset of the mDNS peer
 * list that the user can target with a single broadcast ("kitchen" /
 * "upstairs" etc).
 *
 * Per ADR (docs/multi-room-protocol.md §Group semantics) groups are
 * **not** a protocol concept — each device keeps its own list and the
 * broadcaster intersects the group membership with currently-discovered
 * peers before fanning out. That keeps receivers oblivious (no per-peer
 * group sync), at the cost of groups being device-local.
 *
 * [memberServiceNames] is a Moshi-serialised JSON array of mDNS
 * `serviceName` strings — the same field exposed by
 * [com.opendash.app.util.DiscoveredSpeaker]. Stored as a single
 * TEXT column to avoid a nested Room relation for what is a small,
 * write-rare payload.
 */
@Entity(tableName = "speaker_group")
data class SpeakerGroupEntity(
    @PrimaryKey val name: String,
    val memberServiceNames: String,
    val updatedAtMs: Long
)
