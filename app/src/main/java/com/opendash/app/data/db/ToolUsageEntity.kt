package com.opendash.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tool_usage")
data class ToolUsageEntity(
    @PrimaryKey val toolName: String,
    val totalCalls: Long,
    val successCalls: Long,
    val failureCalls: Long,
    val lastCalledMs: Long
)
