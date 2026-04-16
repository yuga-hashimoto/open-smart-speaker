package com.opensmarthome.speaker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Query("SELECT * FROM memory WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): MemoryEntity?

    @Query("SELECT * FROM memory WHERE `key` LIKE '%' || :query || '%' OR value LIKE '%' || :query || '%' ORDER BY updatedAtMs DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM memory ORDER BY updatedAtMs DESC LIMIT :limit")
    suspend fun list(limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM memory WHERE `key` LIKE :prefix || '%' ORDER BY `key` ASC LIMIT :limit")
    suspend fun listByPrefix(prefix: String, limit: Int): List<MemoryEntity>

    @Query("DELETE FROM memory WHERE `key` = :key")
    suspend fun delete(key: String): Int

    @Query("DELETE FROM memory")
    suspend fun clear()
}
