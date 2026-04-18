package com.opendash.app.tool.memory

import com.opendash.app.data.db.MemoryDao
import com.opendash.app.data.db.MemoryEntity

/**
 * Rebuilds a TF-IDF index from memory rows on each call.
 * For small corpora (~hundreds of entries) this is fine; for larger
 * datasets we'd cache + invalidate on upsert/delete.
 */
class SemanticMemorySearch(
    private val dao: MemoryDao
) {

    suspend fun searchSemantic(query: String, limit: Int = 5): List<MemoryEntity> {
        val all = dao.list(limit = 500)
        if (all.isEmpty()) return emptyList()

        val docs = all.map { e ->
            TfIdfIndex.Document(
                id = e.key,
                text = "${e.key} ${e.value}"
            )
        }
        val index = TfIdfIndex(docs)
        val hits = index.search(query, limit)
        val byId = all.associateBy { it.key }
        return hits.mapNotNull { byId[it.document.id] }
    }
}
