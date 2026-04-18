package com.opendash.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RoutineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(routine: RoutineEntity)

    @Query("SELECT * FROM routine WHERE id = :id LIMIT 1")
    suspend fun get(id: String): RoutineEntity?

    @Query("SELECT * FROM routine WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): RoutineEntity?

    @Query("SELECT * FROM routine ORDER BY updatedAtMs DESC")
    suspend fun listAll(): List<RoutineEntity>

    @Query("DELETE FROM routine WHERE id = :id")
    suspend fun delete(id: String): Int
}
