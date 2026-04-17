package com.opensmarthome.speaker.tool.analytics

import com.opensmarthome.speaker.data.db.ToolUsageDao
import com.opensmarthome.speaker.data.db.ToolUsageEntity

/**
 * UI-facing wrapper around tool usage data.
 * Provides aggregated views the Settings screen can render.
 */
class AnalyticsRepository(
    private val dao: ToolUsageDao,
    private val stats: PersistentToolUsageStats
) {

    data class Summary(
        val totalInvocations: Long,
        val totalSuccesses: Long,
        val totalFailures: Long,
        val uniqueTools: Int,
        val globalSuccessRate: Double
    )

    suspend fun allTime(): List<ToolUsageEntity> = dao.listAll()

    fun currentSession(): List<ToolUsageStats.UsageEntry> = stats.snapshotSession()

    suspend fun topN(n: Int): List<ToolUsageEntity> =
        dao.listAll().take(n.coerceAtLeast(1))

    suspend fun summary(): Summary {
        val entries = dao.listAll()
        val total = entries.sumOf { it.totalCalls }
        val success = entries.sumOf { it.successCalls }
        val failure = entries.sumOf { it.failureCalls }
        return Summary(
            totalInvocations = total,
            totalSuccesses = success,
            totalFailures = failure,
            uniqueTools = entries.size,
            globalSuccessRate = if (total == 0L) 0.0 else success.toDouble() / total
        )
    }

    suspend fun reset() = stats.resetAll()

    /**
     * Clears the persistent tool usage stats (Room table + in-memory session
     * counters). Unlike [reset], this does NOT touch latency metrics — it
     * only zeroes the per-tool invocation rows surfaced by the Settings
     * "Clear tool usage stats" action.
     */
    suspend fun clearToolUsageStats() = stats.resetAll()
}
