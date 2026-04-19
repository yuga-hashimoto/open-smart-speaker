package com.opendash.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent key-value store for agent long-term memory.
 * Inspired by OpenClaw's memory system, simplified to SQL FTS-like
 * substring lookup since we don't have vector embeddings yet.
 *
 * Examples:
 *   key="user.name" value="John"
 *   key="preference.wake_word" value="dash"
 *   key="fact.birthday.wife" value="May 3rd"
 */
@Entity(tableName = "memory")
data class MemoryEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAtMs: Long
)
