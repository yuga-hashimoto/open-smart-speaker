package com.opendash.app.tool.analytics

import com.opendash.app.data.db.ToolUsageDao
import com.opendash.app.data.db.ToolUsageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Room-backed tool usage counter.
 *
 * In-memory first for zero-latency record() (called from the hot path),
 * flushed asynchronously to Room. snapshot() merges in-memory with DB.
 */
class PersistentToolUsageStats(
    private val dao: ToolUsageDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val memoryStats = ToolUsageStats()

    fun record(toolName: String, success: Boolean) {
        memoryStats.record(toolName, success)
        // Fire-and-forget persist
        scope.launch {
            try {
                val existing = dao.get(toolName)
                val now = System.currentTimeMillis()
                val updated = if (existing == null) {
                    ToolUsageEntity(
                        toolName = toolName,
                        totalCalls = 1,
                        successCalls = if (success) 1 else 0,
                        failureCalls = if (success) 0 else 1,
                        lastCalledMs = now
                    )
                } else {
                    existing.copy(
                        totalCalls = existing.totalCalls + 1,
                        successCalls = existing.successCalls + if (success) 1 else 0,
                        failureCalls = existing.failureCalls + if (success) 0 else 1,
                        lastCalledMs = now
                    )
                }
                dao.upsert(updated)
            } catch (e: Exception) {
                Timber.w(e, "Failed to persist tool usage for $toolName")
            }
        }
    }

    suspend fun snapshotAllTime(): List<ToolUsageEntity> = dao.listAll()

    fun snapshotSession(): List<ToolUsageStats.UsageEntry> = memoryStats.snapshot()

    suspend fun resetAll() {
        dao.clear()
        memoryStats.reset()
    }
}
