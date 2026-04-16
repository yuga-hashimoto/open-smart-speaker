package com.opensmarthome.speaker.tool.info

/**
 * User-defined FAQ / knowledge base.
 * Search is keyword-based over question + answer + tags.
 */
interface KnowledgeStore {
    suspend fun search(query: String, limit: Int = 5): List<KnowledgeEntry>
    suspend fun add(entry: KnowledgeEntry)
    suspend fun remove(id: String): Boolean
    suspend fun listAll(): List<KnowledgeEntry>
}
