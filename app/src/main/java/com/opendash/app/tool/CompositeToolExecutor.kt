package com.opendash.app.tool

import com.opendash.app.tool.analytics.ToolUsageRecorder
import timber.log.Timber

/**
 * Combines multiple ToolExecutors into a single executor.
 * Routes tool calls to the appropriate executor based on tool name.
 * Optionally records usage statistics through any ToolUsageRecorder.
 */
class CompositeToolExecutor(
    private val executors: List<ToolExecutor>,
    private val stats: ToolUsageRecorder? = null
) : ToolExecutor {

    private val toolToExecutor = mutableMapOf<String, ToolExecutor>()

    override suspend fun availableTools(): List<ToolSchema> {
        val allTools = mutableListOf<ToolSchema>()
        toolToExecutor.clear()

        for (executor in executors) {
            val tools = executor.availableTools()
            for (tool in tools) {
                toolToExecutor[tool.name] = executor
                allTools.add(tool)
            }
        }

        return allTools
    }

    override suspend fun execute(call: ToolCall): ToolResult {
        val executor = toolToExecutor[call.name]
        val result = if (executor == null) {
            // Try refreshing tool list in case it was not yet loaded
            availableTools()
            val retryExecutor = toolToExecutor[call.name]
                ?: return recordAndReturn(call.name, ToolResult(call.id, false, "", "Unknown tool: ${call.name}"))
            retryExecutor.execute(call)
        } else {
            Timber.d("Routing tool call '${call.name}' to ${executor.javaClass.simpleName}")
            executor.execute(call)
        }
        return recordAndReturn(call.name, result)
    }

    private fun recordAndReturn(toolName: String, result: ToolResult): ToolResult {
        stats?.record(toolName, success = result.success)
        return result
    }
}
