package com.opendash.app.tool.info

/**
 * Fetches news headlines from an RSS/Atom feed.
 * Uses user-configured feed URLs (no API keys needed).
 */
interface NewsProvider {
    suspend fun getHeadlines(feedUrl: String, limit: Int = 10): List<NewsItem>
}

data class NewsItem(
    val title: String,
    val summary: String,
    val link: String,
    val publishedAt: String
)
