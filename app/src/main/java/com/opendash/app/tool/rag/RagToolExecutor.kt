package com.opendash.app.tool.rag

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

class RagToolExecutor(
    private val ragService: RagService
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "ingest_document",
            description = "Store a text document for later retrieval. Use when the user wants you to remember a longer text (article, notes, manual).",
            parameters = mapOf(
                "title" to ToolParameter("string", "Human-readable title", required = true),
                "content" to ToolParameter("string", "The full document text", required = true)
            )
        ),
        ToolSchema(
            name = "retrieve_document",
            description = "Search ingested documents for passages relevant to a query. Returns top-matching chunks with titles.",
            parameters = mapOf(
                "query" to ToolParameter("string", "Question or topic", required = true),
                "limit" to ToolParameter("number", "Max passages (1-5, default 3)", required = false)
            )
        ),
        ToolSchema(
            name = "list_documents",
            description = "List all ingested documents (id + title).",
            parameters = emptyMap()
        ),
        ToolSchema(
            name = "delete_document",
            description = "Remove an ingested document by id.",
            parameters = mapOf(
                "id" to ToolParameter("string", "Document id", required = true)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "ingest_document" -> executeIngest(call)
                "retrieve_document" -> executeRetrieve(call)
                "list_documents" -> executeList(call)
                "delete_document" -> executeDelete(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "RAG tool failed")
            ToolResult(call.id, false, "", e.message ?: "RAG error")
        }
    }

    private suspend fun executeIngest(call: ToolCall): ToolResult {
        val title = call.arguments["title"] as? String
            ?: return ToolResult(call.id, false, "", "Missing title")
        val content = call.arguments["content"] as? String
            ?: return ToolResult(call.id, false, "", "Missing content")
        if (content.isBlank()) {
            return ToolResult(call.id, false, "", "Content is empty")
        }

        val id = ragService.ingest(title, content)
        return ToolResult(call.id, true, """{"document_id":"$id","title":"${title.escapeJson()}"}""")
    }

    private suspend fun executeRetrieve(call: ToolCall): ToolResult {
        val query = call.arguments["query"] as? String
            ?: return ToolResult(call.id, false, "", "Missing query")
        val limit = (call.arguments["limit"] as? Number)?.toInt()?.coerceIn(1, 5) ?: 3

        val hits = ragService.retrieve(query, limit)
        val data = hits.joinToString(",") { hit ->
            """{"title":"${hit.documentTitle.escapeJson()}","content":"${hit.content.escapeJson()}","score":${"%.4f".format(hit.score)}}"""
        }
        return ToolResult(call.id, true, "[$data]")
    }

    private suspend fun executeList(call: ToolCall): ToolResult {
        val docs = ragService.listDocuments()
        val data = docs.joinToString(",") { d ->
            """{"id":"${d.documentId}","title":"${d.title.escapeJson()}"}"""
        }
        return ToolResult(call.id, true, "[$data]")
    }

    private suspend fun executeDelete(call: ToolCall): ToolResult {
        val id = call.arguments["id"] as? String
            ?: return ToolResult(call.id, false, "", "Missing id")
        return if (ragService.deleteDocument(id)) {
            ToolResult(call.id, true, """{"deleted":"$id"}""")
        } else {
            ToolResult(call.id, false, "", "Document not found: $id")
        }
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
}
