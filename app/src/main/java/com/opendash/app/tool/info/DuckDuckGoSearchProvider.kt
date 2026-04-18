package com.opendash.app.tool.info

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Web search using DuckDuckGo Instant Answer API (free, no auth).
 * Docs: https://duckduckgo.com/api
 */
class DuckDuckGoSearchProvider(
    private val client: OkHttpClient,
    private val moshi: Moshi
) : SearchProvider {

    companion object {
        private const val API = "https://api.duckduckgo.com/"
    }

    override suspend fun search(query: String): SearchResult = withContext(Dispatchers.IO) {
        val url = API.toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
            addQueryParameter("format", "json")
            addQueryParameter("no_html", "1")
            addQueryParameter("skip_disambig", "1")
        }.build()

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Search API error: ${response.code}")
            }
            val body = response.body?.string() ?: throw RuntimeException("Empty response")
            parseResult(query, body)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseResult(query: String, json: String): SearchResult {
        val root = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any?>
            ?: throw RuntimeException("Invalid search response")

        val abstract = (root["AbstractText"] as? String).orEmpty()
        val sourceUrl = (root["AbstractURL"] as? String)?.takeIf { it.isNotBlank() }
        val relatedRaw = root["RelatedTopics"] as? List<Map<String, Any?>> ?: emptyList()
        val topics = relatedRaw
            .mapNotNull { it["Text"] as? String }
            .filter { it.isNotBlank() }
            .take(5)

        return SearchResult(
            query = query,
            abstract = abstract,
            sourceUrl = sourceUrl,
            relatedTopics = topics
        )
    }
}
