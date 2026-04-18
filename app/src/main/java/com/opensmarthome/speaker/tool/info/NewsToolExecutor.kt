package com.opensmarthome.speaker.tool.info

import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolParameter
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.tool.ToolSchema
import timber.log.Timber

class NewsToolExecutor(
    private val newsProvider: NewsProvider,
    private val defaultFeeds: List<Pair<String, String>> = DEFAULT_FEEDS
) : ToolExecutor {

    companion object {
        /**
         * (`source=` identifier → RSS URL) pairs. Derived from
         * [BundledNewsFeeds] so the picker, the news tool, and the
         * home-dashboard briefing source all share one source of truth.
         *
         * Backwards-compatible legacy aliases (`bbc`, `nhk`,
         * `hackernews`) are listed first so they win the `firstOrNull`
         * lookup in [resolveFeedUrl] — this preserves the exact URLs
         * that pre-picker installs have been using.
         *
         * Every bundled feed is also reachable via its stable
         * [BundledFeed.id] (e.g. `nhk_society`).
         */
        val DEFAULT_FEEDS: List<Pair<String, String>> =
            BundledNewsFeeds.LEGACY_ALIASES.map { (alias, url) -> alias to url } +
                BundledNewsFeeds.ALL.map { it.id to it.url }
    }

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "get_news",
            description = "Get news headlines from an RSS/Atom feed. Use `source` to pick a bundled feed (bbc, nhk, hackernews) or supply `feed_url`.",
            parameters = mapOf(
                "source" to ToolParameter("string", "Bundled feed name", required = false),
                "feed_url" to ToolParameter("string", "Custom RSS/Atom URL", required = false),
                "limit" to ToolParameter("number", "Max items (1-20, default 5)", required = false)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "get_news" -> executeGetNews(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "News tool failed")
            ToolResult(call.id, false, "", e.message ?: "Feed error")
        }
    }

    private suspend fun executeGetNews(call: ToolCall): ToolResult {
        val feedUrl = resolveFeedUrl(call)
            ?: return ToolResult(call.id, false, "", "No feed_url or valid source provided")
        val limit = (call.arguments["limit"] as? Number)?.toInt()?.coerceIn(1, 20) ?: 5
        val items = newsProvider.getHeadlines(feedUrl, limit)
        val data = items.joinToString(",") { n ->
            """{"title":"${n.title.escapeJson()}","summary":"${n.summary.escapeJson()}","link":"${n.link.escapeJson()}","published":"${n.publishedAt.escapeJson()}"}"""
        }
        return ToolResult(call.id, true, "[$data]")
    }

    private fun resolveFeedUrl(call: ToolCall): String? {
        val direct = call.arguments["feed_url"] as? String
        if (!direct.isNullOrBlank()) return direct
        val source = (call.arguments["source"] as? String)?.lowercase() ?: return null
        return defaultFeeds.firstOrNull { it.first == source }?.second
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
