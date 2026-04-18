package com.opendash.app.tool.analytics

/**
 * Minimal interface for the CompositeToolExecutor to record every invocation,
 * regardless of whether the backing store is in-memory or persisted.
 */
fun interface ToolUsageRecorder {
    fun record(toolName: String, success: Boolean)
}
