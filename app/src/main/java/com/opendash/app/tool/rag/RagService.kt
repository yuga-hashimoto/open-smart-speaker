package com.opendash.app.tool.rag

import com.opendash.app.data.db.DocumentChunkDao
import com.opendash.app.data.db.DocumentChunkEntity
import com.opendash.app.tool.memory.TfIdfIndex
import java.util.UUID

/**
 * Ingests user documents and retrieves the most relevant chunks for a query.
 * Implements TF-IDF retrieval at query time; no background embedding pipeline.
 */
class RagService(
    private val chunkDao: DocumentChunkDao,
    private val chunker: TextChunker = TextChunker()
) {

    data class Hit(val documentTitle: String, val content: String, val score: Double)

    suspend fun ingest(title: String, content: String): String {
        val documentId = UUID.randomUUID().toString()
        val chunks = chunker.chunk(content)
        val now = System.currentTimeMillis()
        val entities = chunks.mapIndexed { idx, text ->
            DocumentChunkEntity(
                documentId = documentId,
                title = title,
                chunkIndex = idx,
                content = text,
                createdAtMs = now
            )
        }
        chunkDao.insertAll(entities)
        return documentId
    }

    suspend fun retrieve(query: String, limit: Int = 3): List<Hit> {
        val allChunks = chunkDao.listAllChunks(limit = 2000)
        if (allChunks.isEmpty()) return emptyList()

        val docs = allChunks.map { c ->
            TfIdfIndex.Document(
                id = "${c.documentId}:${c.chunkIndex}",
                text = c.content
            )
        }
        val index = TfIdfIndex(docs)
        val hits = index.search(query, limit)
        val chunkById = allChunks.associateBy { "${it.documentId}:${it.chunkIndex}" }
        return hits.mapNotNull { hit ->
            val chunk = chunkById[hit.document.id] ?: return@mapNotNull null
            Hit(chunk.title, chunk.content, hit.score)
        }
    }

    suspend fun deleteDocument(documentId: String): Boolean =
        chunkDao.deleteDocument(documentId) > 0

    suspend fun listDocuments(): List<DocumentChunkDao.DocumentTitle> = chunkDao.listDocuments()
}
