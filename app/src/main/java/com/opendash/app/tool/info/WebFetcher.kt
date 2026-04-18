package com.opendash.app.tool.info

/**
 * Fetches a URL and returns readable text content.
 * OpenClaw web.fetch equivalent.
 */
interface WebFetcher {
    suspend fun fetch(url: String, maxChars: Int = 4000): WebPage
}

data class WebPage(
    val url: String,
    val title: String,
    val text: String,
    val statusCode: Int,
    val contentType: String
)
