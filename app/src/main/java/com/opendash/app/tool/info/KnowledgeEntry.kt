package com.opendash.app.tool.info

/**
 * A single FAQ entry — question, answer, and tags for search.
 */
data class KnowledgeEntry(
    val id: String,
    val question: String,
    val answer: String,
    val tags: List<String> = emptyList()
)
