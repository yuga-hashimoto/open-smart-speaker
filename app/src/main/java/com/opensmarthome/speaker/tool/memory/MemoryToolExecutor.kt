package com.opensmarthome.speaker.tool.memory

import com.opensmarthome.speaker.data.db.MemoryDao
import com.opensmarthome.speaker.data.db.MemoryEntity
import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolParameter
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.tool.ToolSchema
import timber.log.Timber

/**
 * Long-term memory tool for the agent.
 * Persists user facts, preferences, and learned context across sessions.
 *
 * Inspired by OpenClaw's memory system (simplified: SQL LIKE search,
 * no vector embeddings yet).
 */
class MemoryToolExecutor(
    private val memoryDao: MemoryDao,
    private val semanticSearch: SemanticMemorySearch = SemanticMemorySearch(memoryDao)
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "remember",
            description = "Save a fact or preference for long-term memory. Use for user preferences, personal facts, or anything you want to recall across sessions. Example: key='user.name', value='Alice'.",
            parameters = mapOf(
                "key" to ToolParameter("string", "Dot-separated memory key (e.g. 'user.name', 'preference.theme')", required = true),
                "value" to ToolParameter("string", "The value to remember", required = true)
            )
        ),
        ToolSchema(
            name = "recall",
            description = "Retrieve a specific memory by exact key.",
            parameters = mapOf(
                "key" to ToolParameter("string", "The memory key to fetch", required = true)
            )
        ),
        ToolSchema(
            name = "search_memory",
            description = "Search long-term memory by keyword (matches keys and values).",
            parameters = mapOf(
                "query" to ToolParameter("string", "Keyword to search", required = true),
                "limit" to ToolParameter("number", "Max results (1-20, default 5)", required = false)
            )
        ),
        ToolSchema(
            name = "forget",
            description = "Delete a specific memory by exact key.",
            parameters = mapOf(
                "key" to ToolParameter("string", "The memory key to delete", required = true)
            )
        ),
        ToolSchema(
            name = "semantic_memory_search",
            description = "Find memories semantically related to a natural-language query (TF-IDF similarity). Use when the user asks 'what do you know about X' without giving an exact key.",
            parameters = mapOf(
                "query" to ToolParameter("string", "Natural language query", required = true),
                "limit" to ToolParameter("number", "Max results (1-20, default 5)", required = false)
            )
        ),
        ToolSchema(
            name = "list_memory",
            description = "List saved memory keys, optionally filtered by key prefix. Useful to explore what's remembered without knowing exact keys. Example: prefix='user.' returns user.name, user.language, user.timezone.",
            parameters = mapOf(
                "prefix" to ToolParameter("string", "Optional key prefix to filter by (empty = list all)", required = false),
                "limit" to ToolParameter("number", "Max results (1-100, default 20)", required = false)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "remember" -> executeRemember(call)
                "recall" -> executeRecall(call)
                "search_memory" -> executeSearch(call)
                "semantic_memory_search" -> executeSemantic(call)
                "list_memory" -> executeList(call)
                "forget" -> executeForget(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Memory tool failed: ${call.name}")
            ToolResult(call.id, false, "", e.message ?: "Memory error")
        }
    }

    private suspend fun executeRemember(call: ToolCall): ToolResult {
        val key = call.arguments["key"] as? String
            ?: return ToolResult(call.id, false, "", "Missing key")
        val value = call.arguments["value"] as? String
            ?: return ToolResult(call.id, false, "", "Missing value")

        memoryDao.upsert(MemoryEntity(key, value, System.currentTimeMillis()))
        return ToolResult(call.id, true, """{"saved":"$key"}""")
    }

    private suspend fun executeRecall(call: ToolCall): ToolResult {
        val key = call.arguments["key"] as? String
            ?: return ToolResult(call.id, false, "", "Missing key")

        val entry = memoryDao.get(key)
            ?: return ToolResult(call.id, false, "", "No memory for key: $key")

        return ToolResult(call.id, true, formatEntry(entry))
    }

    private suspend fun executeSearch(call: ToolCall): ToolResult {
        val query = call.arguments["query"] as? String
            ?: return ToolResult(call.id, false, "", "Missing query")
        val limit = (call.arguments["limit"] as? Number)?.toInt()?.coerceIn(1, 20) ?: 5

        val results = memoryDao.search(query, limit)
        val data = results.joinToString(",") { formatEntry(it) }
        return ToolResult(call.id, true, "[$data]")
    }

    private suspend fun executeSemantic(call: ToolCall): ToolResult {
        val query = call.arguments["query"] as? String
            ?: return ToolResult(call.id, false, "", "Missing query")
        val limit = (call.arguments["limit"] as? Number)?.toInt()?.coerceIn(1, 20) ?: 5

        val results = semanticSearch.searchSemantic(query, limit)
        val data = results.joinToString(",") { formatEntry(it) }
        return ToolResult(call.id, true, "[$data]")
    }

    private suspend fun executeList(call: ToolCall): ToolResult {
        val prefix = (call.arguments["prefix"] as? String)?.trim().orEmpty()
        val limit = (call.arguments["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20

        val results = if (prefix.isEmpty()) {
            memoryDao.list(limit)
        } else {
            memoryDao.listByPrefix(prefix, limit)
        }
        val data = results.joinToString(",") { formatEntry(it) }
        return ToolResult(call.id, true, "[$data]")
    }

    private suspend fun executeForget(call: ToolCall): ToolResult {
        val key = call.arguments["key"] as? String
            ?: return ToolResult(call.id, false, "", "Missing key")

        val count = memoryDao.delete(key)
        return if (count > 0) {
            ToolResult(call.id, true, """{"forgot":"$key"}""")
        } else {
            ToolResult(call.id, false, "", "No memory for key: $key")
        }
    }

    private fun formatEntry(e: MemoryEntity): String =
        """{"key":"${e.key.escapeJson()}","value":"${e.value.escapeJson()}","updated_at":${e.updatedAtMs}}"""

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
