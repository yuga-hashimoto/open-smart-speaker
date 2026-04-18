package com.opendash.app.tool.info

import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Fetches a URL and extracts readable text via regex-based HTML stripping.
 * Zero extra dependencies; not a full reader-mode but good enough for most
 * article-style pages when the query is narrow.
 */
class HtmlWebFetcher(
    private val client: OkHttpClient
) : WebFetcher {

    override suspend fun fetch(url: String, maxChars: Int): WebPage {
        val request = Request.Builder().url(url).get()
            .header("User-Agent", "OpenDash/1.0 (+android)")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val contentType = response.header("Content-Type").orEmpty()
            val title = extractTitle(body)
            val text = if (contentType.startsWith("text/html") || body.trimStart().startsWith("<")) {
                stripHtml(body)
            } else {
                body
            }
            return WebPage(
                url = url,
                title = title,
                text = text.take(maxChars),
                statusCode = response.code,
                contentType = contentType
            )
        }
    }

    internal fun extractTitle(html: String): String =
        TITLE_REGEX.find(html)?.groupValues?.get(1)?.trim()?.decodeEntities() ?: ""

    internal fun stripHtml(html: String): String {
        return html
            .replace(SCRIPT_REGEX, "")
            .replace(STYLE_REGEX, "")
            .replace(COMMENT_REGEX, "")
            .replace(TAG_REGEX, " ")
            .decodeEntities()
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private fun String.decodeEntities(): String =
        replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")

    companion object {
        private val TITLE_REGEX = """<title[^>]*>([\s\S]*?)</title>""".toRegex(RegexOption.IGNORE_CASE)
        private val SCRIPT_REGEX = """<script\b[\s\S]*?</script>""".toRegex(RegexOption.IGNORE_CASE)
        private val STYLE_REGEX = """<style\b[\s\S]*?</style>""".toRegex(RegexOption.IGNORE_CASE)
        private val COMMENT_REGEX = """<!--[\s\S]*?-->""".toRegex()
        private val TAG_REGEX = """<[^>]+>""".toRegex()
        private val WHITESPACE_REGEX = """\s+""".toRegex()
    }
}
