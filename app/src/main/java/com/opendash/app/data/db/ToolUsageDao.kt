package com.opendash.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ToolUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ToolUsageEntity)

    @Query("SELECT * FROM tool_usage WHERE toolName = :name LIMIT 1")
    suspend fun get(name: String): ToolUsageEntity?

    @Query("SELECT * FROM tool_usage ORDER BY totalCalls DESC")
    suspend fun listAll(): List<ToolUsageEntity>

    @Query("DELETE FROM tool_usage")
    suspend fun clear()
}
