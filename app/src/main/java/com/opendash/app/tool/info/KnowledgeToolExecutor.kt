package com.opendash.app.tool.info

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

class KnowledgeToolExecutor(
    private val store: KnowledgeStore
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "search_knowledge",
            description = "Search the user's personal FAQ/knowledge base for an answer. Use before falling back to web search when the user asks about something they've taught you.",
            parameters = mapOf(
                "query" to ToolParameter("string", "Keywords to search for", required = true),
                "limit" to ToolParameter("number", "Max results (1-10, default 3)", required = false)
            )
        ),
        ToolSchema(
            name = "add_knowledge",
            description = "Teach the agent a new Q&A. Use when the user explicitly says 'remember that ...' with a question-answer structure.",
            parameters = mapOf(
                "question" to ToolParameter("string", "The question", required = true),
                "answer" to ToolParameter("string", "The answer", required = true),
                "tags" to ToolParameter("string", "Comma-separated tags (optional)", required = false)
            )
        ),
        ToolSchema(
            name = "remove_knowledge",
            description = "Remove a knowledge entry by id.",
            parameters = mapOf(
                "id" to ToolParameter("string", "The knowledge entry id", required = true)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "search_knowledge" -> executeSearch(call)
                "add_knowledge" -> executeAdd(call)
                "remove_knowledge" -> executeRemove(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Knowledge tool failed: ${call.name}")
            ToolResult(call.id, false, "", e.message ?: "Knowledge error")
        }
    }

    private suspend fun executeSearch(call: ToolCall): ToolResult {
        val query = call.arguments["query"] as? String
            ?: return ToolResult(call.id, false, "", "Missing query")
        val limit = (call.arguments["limit"] as? Number)?.toInt()?.coerceIn(1, 10) ?: 3

        val results = store.search(query, limit)
        val data = results.joinToString(",") { format(it) }
        return ToolResult(call.id, true, "[$data]")
    }

    private suspend fun executeAdd(call: ToolCall): ToolResult {
        val question = call.arguments["question"] as? String
            ?: return ToolResult(call.id, false, "", "Missing question")
        val answer = call.arguments["answer"] as? String
            ?: return ToolResult(call.id, false, "", "Missing answer")
        val tags = (call.arguments["tags"] as? String)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        val entry = KnowledgeEntry(
            id = "",
            question = question,
            answer = answer,
            tags = tags
        )
        store.add(entry)
        return ToolResult(call.id, true, """{"added":"${question.escapeJson()}"}""")
    }

    private suspend fun executeRemove(call: ToolCall): ToolResult {
        val id = call.arguments["id"] as? String
            ?: return ToolResult(call.id, false, "", "Missing id")

        return if (store.remove(id)) {
            ToolResult(call.id, true, """{"removed":"$id"}""")
        } else {
            ToolResult(call.id, false, "", "No entry with id: $id")
        }
    }

    private fun format(e: KnowledgeEntry): String {
        val tags = e.tags.joinToString(",") { """"${it.escapeJson()}"""" }
        return """{"id":"${e.id}","question":"${e.question.escapeJson()}","answer":"${e.answer.escapeJson()}","tags":[$tags]}"""
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
