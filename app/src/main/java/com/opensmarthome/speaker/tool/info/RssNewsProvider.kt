package com.opensmarthome.speaker.tool.info

import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Parses RSS 2.0 and Atom feeds with regex-based extraction.
 * No dependency on Android XmlPullParser so unit-testable on JVM.
 */
class RssNewsProvider(
    private val client: OkHttpClient
) : NewsProvider {

    override suspend fun getHeadlines(feedUrl: String, limit: Int): List<NewsItem> {
        val body = fetch(feedUrl)
        return parseFeed(body, limit)
    }

    private fun fetch(url: String): String {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Feed fetch failed: ${response.code}")
            }
            return response.body?.string() ?: throw RuntimeException("Empty feed")
        }
    }

    internal fun parseFeed(xml: String, limit: Int): List<NewsItem> {
        // Matches <item>...</item> (RSS) or <entry>...</entry> (Atom)
        val itemRegex = """<(item|entry)\b[^>]*>([\s\S]*?)</\1>""".toRegex(RegexOption.IGNORE_CASE)

        return itemRegex.findAll(xml)
            .take(limit)
            .mapNotNull { match ->
                val block = match.groupValues[2]
                val title = extractText(block, "title") ?: return@mapNotNull null
                val summary = extractText(block, "description")
                    ?: extractText(block, "summary")
                    ?: extractText(block, "content")
                    ?: ""
                val link = extractAtomLink(block) ?: extractText(block, "link") ?: ""
                val published = extractText(block, "pubDate")
                    ?: extractText(block, "published")
                    ?: extractText(block, "updated")
                    ?: ""

                NewsItem(
                    title = decodeEntities(title),
                    summary = decodeEntities(summary.take(300)),
                    link = link.trim(),
                    publishedAt = published.trim()
                )
            }
            .toList()
    }

    private fun extractText(block: String, tag: String): String? {
        val regex = """<$tag\b[^>]*>([\s\S]*?)</$tag>""".toRegex(RegexOption.IGNORE_CASE)
        val content = regex.find(block)?.groupValues?.get(1) ?: return null
        val cleaned = content
            .replace(CDATA_REGEX) { it.groupValues[1] }
            .replace(TAG_REGEX, "")
            .trim()
        return cleaned.ifBlank { null }
    }

    private fun extractAtomLink(block: String): String? {
        // Atom: <link href="..." />
        return ATOM_LINK_REGEX.find(block)?.groupValues?.get(1)
    }

    private fun decodeEntities(text: String): String =
        text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")

    companion object {
        private val CDATA_REGEX = """<!\[CDATA\[([\s\S]*?)\]\]>""".toRegex()
        private val TAG_REGEX = """<[^>]+>""".toRegex()
        private val ATOM_LINK_REGEX = """<link\b[^>]*\bhref="([^"]+)"[^>]*/?>""".toRegex(RegexOption.IGNORE_CASE)
    }
}
