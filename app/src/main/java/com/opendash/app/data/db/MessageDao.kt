package com.opendash.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySessionId(sessionId: String): List<MessageEntity>

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)
}
