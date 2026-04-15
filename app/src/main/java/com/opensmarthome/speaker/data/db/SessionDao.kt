package com.opensmarthome.speaker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    suspend fun getAll(): List<SessionEntity>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: String)
}
