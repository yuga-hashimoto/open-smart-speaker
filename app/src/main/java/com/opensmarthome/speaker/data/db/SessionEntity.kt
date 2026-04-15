package com.opensmarthome.speaker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val createdAt: Long
)
