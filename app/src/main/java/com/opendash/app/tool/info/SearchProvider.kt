package com.opendash.app.tool.info

/**
 * Performs web searches and returns summaries.
 */
interface SearchProvider {
    suspend fun search(query: String): SearchResult
}

data class SearchResult(
    val query: String,
    val abstract: String,
    val sourceUrl: String?,
    val relatedTopics: List<String>
)
