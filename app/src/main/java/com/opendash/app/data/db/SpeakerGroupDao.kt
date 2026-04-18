package com.opendash.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeakerGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: SpeakerGroupEntity)

    @Query("SELECT * FROM speaker_group WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): SpeakerGroupEntity?

    @Query("SELECT * FROM speaker_group ORDER BY name COLLATE NOCASE ASC")
    suspend fun listAll(): List<SpeakerGroupEntity>

    @Query("SELECT * FROM speaker_group ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SpeakerGroupEntity>>

    @Query("DELETE FROM speaker_group WHERE name = :name COLLATE NOCASE")
    suspend fun delete(name: String): Int
}
