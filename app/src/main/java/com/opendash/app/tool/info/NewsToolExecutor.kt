package com.opendash.app.tool.info

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

class NewsToolExecutor(
    private val newsProvider: NewsProvider,
    private val defaultFeeds: List<Pair<String, String>> = DEFAULT_FEEDS,
    private val defaultFeedUrlProvider: suspend () -> String? = { null }
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
            description = "Get news headlines from an RSS/Atom feed. Use `source` to pick a bundled feed (bbc, nhk, hackernews) or supply `feed_url`. If neither is given, falls back to the user-configured default (or NHK General).",
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
        val limit = (call.arguments["limit"] as? Number)?.toInt()?.coerceIn(1, 20) ?: 5
        val items = newsProvider.getHeadlines(feedUrl, limit)
        val data = items.joinToString(",") { n ->
            """{"title":"${n.title.escapeJson()}","summary":"${n.summary.escapeJson()}","link":"${n.link.escapeJson()}","published":"${n.publishedAt.escapeJson()}"}"""
        }
        return ToolResult(call.id, true, "[$data]")
    }

    /**
     * Resolve the feed URL to fetch, applying a fallback chain so that a
     * bare "ニュース教えて" / "news" fast-path utterance — which arrives with
     * no arguments — still returns something useful instead of failing.
     *
     * Resolution order:
     * 1. Explicit `feed_url` argument wins outright.
     * 2. Known `source=` alias (`bbc`, `nhk`, `hackernews`, or any
     *    [BundledFeed.id]).
     * 3. User-configured default from Settings → News
     *    (`DEFAULT_NEWS_FEED_URL` preference).
     * 4. [BundledNewsFeeds.NHK_GENERAL] — preserves the historical
     *    backward-compat default used by pre-picker installs.
     *
     * Unknown `source=` values fall through to 3 → 4 rather than failing —
     * the user asked for news, so answer with the best available default
     * instead of an error.
     */
    private suspend fun resolveFeedUrl(call: ToolCall): String {
        val direct = (call.arguments["feed_url"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        if (direct != null) return direct

        val source = (call.arguments["source"] as? String)
            ?.lowercase()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (source != null) {
            val bundled = defaultFeeds.firstOrNull { it.first == source }?.second
            if (bundled != null) return bundled
        }

        val userDefault = runCatching { defaultFeedUrlProvider() }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return userDefault ?: BundledNewsFeeds.NHK_GENERAL.url
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
