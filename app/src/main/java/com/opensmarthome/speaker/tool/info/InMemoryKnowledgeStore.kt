package com.opensmarthome.speaker.tool.info

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory implementation. Persistence left to wrappers.
 * Scoring: count of query terms matched in question+answer+tags (case-insensitive).
 */
class InMemoryKnowledgeStore(
    initial: List<KnowledgeEntry> = emptyList()
) : KnowledgeStore {

    private val entries = ConcurrentHashMap<String, KnowledgeEntry>()

    init {
        initial.forEach { entries[it.id] = it }
    }

    override suspend fun search(query: String, limit: Int): List<KnowledgeEntry> {
        val terms = query.lowercase().split(WHITESPACE).filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()

        return entries.values
            .map { it to score(it, terms) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    override suspend fun add(entry: KnowledgeEntry) {
        val id = entry.id.ifBlank { UUID.randomUUID().toString() }
        entries[id] = entry.copy(id = id)
    }

    override suspend fun remove(id: String): Boolean = entries.remove(id) != null

    override suspend fun listAll(): List<KnowledgeEntry> = entries.values.toList()

    private fun score(entry: KnowledgeEntry, terms: List<String>): Int {
        val haystack = buildString {
            append(entry.question.lowercase())
            append(' ')
            append(entry.answer.lowercase())
            append(' ')
            append(entry.tags.joinToString(" ") { it.lowercase() })
        }
        return terms.count { term -> haystack.contains(term) }
    }

    companion object {
        private val WHITESPACE = Regex("""\s+""")
    }
}
