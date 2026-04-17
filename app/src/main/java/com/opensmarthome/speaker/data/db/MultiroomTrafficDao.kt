package com.opensmarthome.speaker.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [MultiroomTrafficEntity]. The only write path is
 * [upsertIncrement] — callers never need to read-modify-write on their
 * own because the atomic INSERT ... ON CONFLICT keeps concurrent
 * inbound/outbound recorders from racing on the same row.
 */
@Dao
interface MultiroomTrafficDao {

    /**
     * Increment the counter for (type, direction) by 1 and update
     * [MultiroomTrafficEntity.lastAtMs] to [nowMs]. Inserts a row with
     * count=1 if the pair has never been observed before.
     *
     * Executed as a single SQLite statement so back-to-back inbound
     * heartbeats can't lose counts even under contention — the read,
     * add, and write all happen server-side inside the DB engine.
     */
    @Query(
        """
        INSERT INTO multiroom_traffic (type, direction, count, lastAtMs)
        VALUES (:type, :direction, 1, :nowMs)
        ON CONFLICT(type, direction) DO UPDATE SET
            count = count + 1,
            lastAtMs = :nowMs
        """
    )
    suspend fun upsertIncrement(type: String, direction: String, nowMs: Long)

    /**
     * Reactive stream of every row, ordered by (type, direction) so the
     * UI can index rows by type without having to re-group on each emit.
     */
    @Query("SELECT * FROM multiroom_traffic ORDER BY type ASC, direction ASC")
    fun observe(): Flow<List<MultiroomTrafficEntity>>

    @Query("SELECT * FROM multiroom_traffic ORDER BY type ASC, direction ASC")
    suspend fun listAll(): List<MultiroomTrafficEntity>

    @Query("DELETE FROM multiroom_traffic")
    suspend fun clear()
}
