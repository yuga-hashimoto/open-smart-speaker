package com.opendash.app.tool.info

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

class SearchToolExecutor(
    private val searchProvider: SearchProvider
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "web_search",
            description = "Search the web for information. Returns a summary and related topics.",
            parameters = mapOf(
                "query" to ToolParameter("string", "The search query", required = true)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "web_search" -> executeSearch(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Search tool failed: ${call.name}")
            ToolResult(call.id, false, "", e.message ?: "Search failed")
        }
    }

    private suspend fun executeSearch(call: ToolCall): ToolResult {
        val query = call.arguments["query"] as? String
            ?: return ToolResult(call.id, false, "", "Missing query")

        val result = searchProvider.search(query)
        val topics = result.relatedTopics.joinToString(",") { "\"${it.escapeJson()}\"" }
        val abstract = result.abstract.escapeJson()
        val sourceUrl = result.sourceUrl?.let { "\"$it\"" } ?: "null"

        val data = """{"query":"${result.query.escapeJson()}","abstract":"$abstract","source_url":$sourceUrl,"related":[$topics]}"""
        return ToolResult(call.id, true, data)
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
}
