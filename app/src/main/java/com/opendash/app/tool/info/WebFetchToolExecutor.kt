package com.opendash.app.tool.info

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

class WebFetchToolExecutor(
    private val fetcher: WebFetcher
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "web_fetch",
            description = "Download a web page and return its title and readable text. Use for articles, docs, recipes, etc.",
            parameters = mapOf(
                "url" to ToolParameter("string", "The URL to fetch (https:// preferred)", required = true),
                "max_chars" to ToolParameter("number", "Max characters to return (default 4000)", required = false)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "web_fetch" -> executeFetch(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Web fetch failed")
            ToolResult(call.id, false, "", e.message ?: "Fetch failed")
        }
    }

    private suspend fun executeFetch(call: ToolCall): ToolResult {
        val url = call.arguments["url"] as? String
            ?: return ToolResult(call.id, false, "", "Missing url")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult(call.id, false, "", "URL must start with http:// or https://")
        }
        val maxChars = (call.arguments["max_chars"] as? Number)?.toInt()?.coerceIn(200, 16000) ?: 4000

        val page = fetcher.fetch(url, maxChars)
        val data = """{"url":"${page.url.escapeJson()}","title":"${page.title.escapeJson()}","text":"${page.text.escapeJson()}","status":${page.statusCode}}"""
        return ToolResult(call.id, page.statusCode in 200..299, data,
            if (page.statusCode !in 200..299) "HTTP ${page.statusCode}" else null)
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
}
