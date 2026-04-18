package com.opendash.app.tool.analytics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe in-memory counter of LLM tool invocations.
 * Lets the UI show "your top tools this week" or helps us
 * decide which capabilities to surface more prominently.
 *
 * Data stays on device; never sent anywhere.
 */
class ToolUsageStats {

    data class UsageEntry(
        val toolName: String,
        val totalCalls: Long,
        val successCalls: Long,
        val failureCalls: Long,
        val lastCalledMs: Long
    ) {
        val successRate: Double
            get() = if (totalCalls == 0L) 0.0 else successCalls.toDouble() / totalCalls
    }

    private data class Counters(
        val total: AtomicLong = AtomicLong(0),
        val success: AtomicLong = AtomicLong(0),
        val failure: AtomicLong = AtomicLong(0),
        val lastMs: AtomicLong = AtomicLong(0)
    )

    private val stats = ConcurrentHashMap<String, Counters>()

    fun record(toolName: String, success: Boolean, atMs: Long = System.currentTimeMillis()) {
        val c = stats.computeIfAbsent(toolName) { Counters() }
        c.total.incrementAndGet()
        if (success) c.success.incrementAndGet() else c.failure.incrementAndGet()
        c.lastMs.set(atMs)
    }

    fun snapshot(): List<UsageEntry> =
        stats.map { (name, c) ->
            UsageEntry(
                toolName = name,
                totalCalls = c.total.get(),
                successCalls = c.success.get(),
                failureCalls = c.failure.get(),
                lastCalledMs = c.lastMs.get()
            )
        }.sortedByDescending { it.totalCalls }

    fun reset() {
        stats.clear()
    }

    fun totalInvocations(): Long = stats.values.sumOf { it.total.get() }
}
