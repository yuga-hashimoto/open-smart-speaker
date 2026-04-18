package com.opendash.app.tool.rag

import com.opendash.app.data.db.DocumentChunkDao

/**
 * UI-facing wrapper for the document ingest picker screen.
 */
class RagRepository(
    private val service: RagService,
    private val dao: DocumentChunkDao
) {

    data class DocumentSummary(
        val id: String,
        val title: String,
        val chunkCount: Int
    )

    suspend fun listDocuments(): List<DocumentSummary> {
        val titles = dao.listDocuments()
        // Count chunks per document by grouping existing listAllChunks result
        val chunks = dao.listAllChunks(limit = 10_000).groupingBy { it.documentId }.eachCount()
        return titles.map { t ->
            DocumentSummary(
                id = t.documentId,
                title = t.title,
                chunkCount = chunks[t.documentId] ?: 0
            )
        }
    }

    suspend fun ingest(title: String, content: String): String {
        require(title.isNotBlank()) { "title must not be blank" }
        require(content.isNotBlank()) { "content must not be blank" }
        return service.ingest(title, content)
    }

    suspend fun delete(id: String): Boolean = service.deleteDocument(id)

    suspend fun retrieve(query: String, limit: Int = 3): List<RagService.Hit> =
        service.retrieve(query, limit)
}
