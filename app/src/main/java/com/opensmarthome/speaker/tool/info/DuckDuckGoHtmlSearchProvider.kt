package com.opensmarthome.speaker.tool.info

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Web search by scraping the DuckDuckGo HTML endpoint.
 *
 * Unlike the Instant Answer API ([DuckDuckGoSearchProvider]) which almost
 * always returns empty payloads for general web queries, the HTML endpoint
 * returns a real SERP. We parse it with regex (no HTML dependency) following
 * the approach used by the openclaw TypeScript extension.
 *
 * Stolen from openclaw: `extensions/duckduckgo/src/ddg-client.ts`.
 *
 * Endpoint: `https://html.duckduckgo.com/html?q={encoded}`
 * We send a desktop Chrome User-Agent so we get the normal SERP rather than
 * a bot-challenge page. If a challenge page is detected we throw so callers
 * can fall back to another provider.
 */
class DuckDuckGoHtmlSearchProvider(
    private val client: OkHttpClient,
    private val endpointProvider: () -> String = { DEFAULT_ENDPOINT },
    private val maxResults: Int = DEFAULT_MAX_RESULTS,
) : SearchProvider {

    override suspend fun search(query: String): SearchResult = withContext(Dispatchers.IO) {
        val url = endpointProvider().toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
        }.build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", CHROME_USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("DuckDuckGo HTML error: ${response.code}")
            }
            val html = response.body?.string().orEmpty()
            if (isBotChallenge(html)) {
                throw RuntimeException("DuckDuckGo returned a bot-detection challenge.")
            }
            val parsed = parseResults(html).take(maxResults)
            val top = parsed.firstOrNull()
            SearchResult(
                query = query,
                abstract = top?.snippet?.takeIf { it.isNotBlank() } ?: top?.title.orEmpty(),
                sourceUrl = top?.url,
                relatedTopics = parsed.drop(1).map { it.title }.filter { it.isNotBlank() }
            )
        }
    }

    internal data class HtmlResult(val title: String, val url: String, val snippet: String)

    internal fun parseResults(html: String): List<HtmlResult> {
        val results = mutableListOf<HtmlResult>()
        val matches = RESULT_A_REGEX.findAll(html).toList()
        matches.forEachIndexed { index, match ->
            val attributes = match.groupValues[1]
            val rawTitle = match.groupValues[2]
            val href = HREF_REGEX.find(attributes)?.groupValues?.get(1).orEmpty()
            // Scope snippet search to text between this <a class="result__a"> and the next.
            val sliceStart = match.range.last + 1
            val sliceEnd = matches.getOrNull(index + 1)?.range?.first ?: html.length
            val scope = html.substring(sliceStart, sliceEnd)
            val rawSnippet = SNIPPET_REGEX.find(scope)?.groupValues?.get(1).orEmpty()
            val title = decodeEntities(stripHtml(rawTitle))
            val url = decodeDuckDuckGoUrl(decodeEntities(href))
            val snippet = decodeEntities(stripHtml(rawSnippet))
            if (title.isNotBlank() && url.isNotBlank()) {
                results += HtmlResult(title = title, url = url, snippet = snippet)
            }
        }
        return results
    }

    internal fun isBotChallenge(html: String): Boolean {
        if (RESULT_A_CLASS_REGEX.containsMatchIn(html)) return false
        return BOT_CHALLENGE_REGEX.containsMatchIn(html)
    }

    internal fun decodeDuckDuckGoUrl(raw: String): String {
        if (raw.isBlank()) return raw
        val normalized = if (raw.startsWith("//")) "https:$raw" else raw
        // Use OkHttp's HttpUrl (JVM-safe — works in unit tests unlike
        // android.net.Uri which is a stub on the host JVM).
        val httpUrl = normalized.toHttpUrlOrNull() ?: return raw
        val uddg = httpUrl.queryParameter("uddg")
        return uddg ?: raw
    }

    internal fun stripHtml(html: String): String =
        html.replace(TAG_REGEX, " ").replace(WHITESPACE_REGEX, " ").trim()

    internal fun decodeEntities(text: String): String {
        var s = text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&#x2F;", "/")
            .replace("&nbsp;", " ")
            .replace("&ndash;", "-")
            .replace("&mdash;", "--")
            .replace("&hellip;", "...")
        s = NUMERIC_ENTITY_REGEX.replace(s) { match ->
            runCatching { String(Character.toChars(match.groupValues[1].toInt())) }
                .getOrDefault(match.value)
        }
        s = HEX_ENTITY_REGEX.replace(s) { match ->
            runCatching { String(Character.toChars(match.groupValues[1].toInt(16))) }
                .getOrDefault(match.value)
        }
        return s
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://html.duckduckgo.com/html"
        const val DEFAULT_MAX_RESULTS = 10

        // Mirrors the UA used by openclaw's ddg-client so we consistently
        // receive SERP HTML rather than a challenge page.
        private const val CHROME_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

        private val RESULT_A_REGEX =
            Regex(
                """<a\b(?=[^>]*\bclass="[^"]*\bresult__a\b[^"]*")([^>]*)>([\s\S]*?)</a>""",
                RegexOption.IGNORE_CASE
            )
        private val RESULT_A_CLASS_REGEX =
            Regex("""class="[^"]*\bresult__a\b[^"]*"""", RegexOption.IGNORE_CASE)
        private val SNIPPET_REGEX =
            Regex(
                """<a\b(?=[^>]*\bclass="[^"]*\bresult__snippet\b[^"]*")[^>]*>([\s\S]*?)</a>""",
                RegexOption.IGNORE_CASE
            )
        private val HREF_REGEX =
            Regex("""\bhref="([^"]*)"""", RegexOption.IGNORE_CASE)
        private val BOT_CHALLENGE_REGEX =
            Regex(
                """g-recaptcha|are you a human|id="challenge-form"|name="challenge"""",
                RegexOption.IGNORE_CASE
            )
        private val TAG_REGEX = Regex("""<[^>]+>""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
        private val NUMERIC_ENTITY_REGEX = Regex("""&#(\d+);""")
        private val HEX_ENTITY_REGEX = Regex("""&#x([0-9a-fA-F]+);""")
    }
}
