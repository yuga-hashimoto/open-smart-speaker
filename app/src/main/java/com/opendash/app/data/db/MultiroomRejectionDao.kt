package com.opendash.app.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [MultiroomRejectionEntity]. Atomic INSERT ... ON CONFLICT
 * keeps concurrent rejections from racing on the same row.
 */
@Dao
interface MultiroomRejectionDao {

    /**
     * Increment the counter for [reason] by 1 and update [lastAtMs] to
     * [nowMs]. Inserts a row with count=1 if the reason has never been
     * observed before. Executes as a single SQLite statement.
     */
    @Query(
        """
        INSERT INTO multiroom_rejections (reason, count, lastAtMs)
        VALUES (:reason, 1, :nowMs)
        ON CONFLICT(reason) DO UPDATE SET
            count = count + 1,
            lastAtMs = :nowMs
        """
    )
    suspend fun upsertIncrement(reason: String, nowMs: Long)

    /**
     * Reactive stream of every rejection row, ordered by reason for a
     * stable UI render.
     */
    @Query("SELECT * FROM multiroom_rejections ORDER BY reason ASC")
    fun observe(): Flow<List<MultiroomRejectionEntity>>

    @Query("SELECT * FROM multiroom_rejections ORDER BY reason ASC")
    suspend fun listAll(): List<MultiroomRejectionEntity>

    @Query("DELETE FROM multiroom_rejections")
    suspend fun clear()
}
