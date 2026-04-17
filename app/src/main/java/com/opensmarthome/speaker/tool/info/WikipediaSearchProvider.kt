package com.opensmarthome.speaker.tool.info

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Language-aware search provider that queries the public Wikipedia REST API.
 *
 * Wikipedia REST endpoints used:
 *  1. Open search (title suggestion):
 *     `https://{lang}.wikipedia.org/w/api.php?action=opensearch&search={q}&limit=3&format=json`
 *  2. Page summary (extract text):
 *     `https://{lang}.wikipedia.org/api/rest_v1/page/summary/{title}`
 *
 * Both endpoints are public, unauthenticated, and free for non-abusive use.
 * They are the canonical fallback for the DuckDuckGo Instant Answer API which
 * frequently returns empty responses for general queries.
 */
interface LanguageAwareSearchProvider {
    suspend fun search(query: String, lang: String): SearchResult?
}

class WikipediaSearchProvider(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val baseUrlProvider: (String) -> String = { lang -> "https://$lang.wikipedia.org" }
) : LanguageAwareSearchProvider {

    override suspend fun search(query: String, lang: String): SearchResult? = withContext(Dispatchers.IO) {
        val title = topSearchTitle(query, lang) ?: return@withContext null
        val extract = fetchSummary(title, lang) ?: return@withContext null
        if (extract.text.isBlank()) null else SearchResult(
            query = query,
            abstract = extract.text,
            sourceUrl = extract.url,
            relatedTopics = emptyList()
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun topSearchTitle(query: String, lang: String): String? {
        val url = "${baseUrlProvider(lang)}/w/api.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("action", "opensearch")
            addQueryParameter("search", query)
            addQueryParameter("limit", "3")
            addQueryParameter("namespace", "0")
            addQueryParameter("format", "json")
        }.build()

        val request = Request.Builder().url(url).get().build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                // OpenSearch returns a JSON array: [query, titles, descriptions, urls]
                val root = moshi.adapter(List::class.java).fromJson(body) as? List<Any?>
                    ?: return@use null
                val titles = (root.getOrNull(1) as? List<String>).orEmpty()
                titles.firstOrNull { it.isNotBlank() }
            }
        }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun fetchSummary(title: String, lang: String): Summary? {
        // The REST summary endpoint accepts titles with spaces encoded as %20
        // when added via HttpUrl.newBuilder (underscores also work).
        val url = "${baseUrlProvider(lang)}/api/rest_v1/page/summary/".toHttpUrl()
            .newBuilder()
            .addPathSegment(title)
            .build()

        val request = Request.Builder().url(url).get().build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val root = moshi.adapter(Map::class.java).fromJson(body) as? Map<String, Any?>
                    ?: return@use null
                val extract = (root["extract"] as? String).orEmpty()
                val pageUrl = ((root["content_urls"] as? Map<String, Any?>)
                    ?.get("desktop") as? Map<String, Any?>)
                    ?.get("page") as? String
                Summary(extract, pageUrl)
            }
        }.getOrNull()
    }

    private data class Summary(val text: String, val url: String?)
}
