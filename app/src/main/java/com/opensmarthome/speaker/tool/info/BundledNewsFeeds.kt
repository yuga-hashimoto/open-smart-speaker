package com.opensmarthome.speaker.tool.info

import androidx.annotation.StringRes
import com.opensmarthome.speaker.R

/**
 * Coarse category grouping for the settings picker. Keeps the Dialog
 * organized ("NHK (日本語)" / "BBC (English)" / "Other") and also lets
 * future voice fast-paths ("news about politics") map an utterance to
 * a compatible feed without a hard-coded outlet match.
 */
enum class NewsFeedCategory {
    General,
    Society,
    Politics,
    Economy,
    World,
    Sports,
    Culture,
    Science,
    Technology,
    Business,
    Tech
}

/**
 * Provenance of a feed — drives the category header in the picker and
 * keeps localized "NHK (日本語)" / "BBC (English)" / "Other" grouping
 * stable regardless of translation order.
 */
enum class NewsFeedSource {
    Nhk,
    Bbc,
    Other
}

/**
 * A single bundled RSS feed offered to the user in the News picker.
 * `id` is a stable identifier suitable for persistence & analytics;
 * it also doubles as the `source=` argument of [NewsToolExecutor] so
 * voice-controlled "news from BBC" routes still resolve correctly.
 *
 * Immutable data class — no state held here, pure configuration.
 */
data class BundledFeed(
    val id: String,
    @StringRes val labelResId: Int,
    val url: String,
    val category: NewsFeedCategory,
    val source: NewsFeedSource
)

/**
 * Curated, fixed list of bundled feeds. Mirrors dicio-android's
 * `ListSetting` approach — a closed enum-like set of options that the
 * Settings picker can render as radio buttons, plus a "custom URL"
 * escape hatch rendered by the row itself.
 *
 * Kept deliberately small: the goal is "good defaults for a freshly-set
 * up device", not "every RSS feed ever". Power users pick "Custom URL".
 *
 * Ordering matches the picker's expected visual order (NHK block first
 * because the project's primary language is Japanese; BBC second because
 * English is the most-requested fallback in existing installs; HN last).
 */
object BundledNewsFeeds {

    /** NHK 総合 — preserved as the backward-compat default for pre-setting installs. */
    val NHK_GENERAL = BundledFeed(
        id = "nhk_general",
        labelResId = R.string.news_feed_nhk_general,
        url = "https://www3.nhk.or.jp/rss/news/cat0.xml",
        category = NewsFeedCategory.General,
        source = NewsFeedSource.Nhk
    )

    val NHK_SOCIETY = BundledFeed(
        id = "nhk_society",
        labelResId = R.string.news_feed_nhk_society,
        url = "https://www3.nhk.or.jp/rss/news/cat1.xml",
        category = NewsFeedCategory.Society,
        source = NewsFeedSource.Nhk
    )

    val NHK_SCIENCE = BundledFeed(
        id = "nhk_science",
        labelResId = R.string.news_feed_nhk_science,
        url = "https://www3.nhk.or.jp/rss/news/cat2.xml",
        category = NewsFeedCategory.Science,
        source = NewsFeedSource.Nhk
    )

    val NHK_POLITICS = BundledFeed(
        id = "nhk_politics",
        labelResId = R.string.news_feed_nhk_politics,
        url = "https://www3.nhk.or.jp/rss/news/cat3.xml",
        category = NewsFeedCategory.Politics,
        source = NewsFeedSource.Nhk
    )

    val NHK_ECONOMY = BundledFeed(
        id = "nhk_economy",
        labelResId = R.string.news_feed_nhk_economy,
        url = "https://www3.nhk.or.jp/rss/news/cat4.xml",
        category = NewsFeedCategory.Economy,
        source = NewsFeedSource.Nhk
    )

    val NHK_WORLD = BundledFeed(
        id = "nhk_world",
        labelResId = R.string.news_feed_nhk_world,
        url = "https://www3.nhk.or.jp/rss/news/cat6.xml",
        category = NewsFeedCategory.World,
        source = NewsFeedSource.Nhk
    )

    val NHK_SPORTS = BundledFeed(
        id = "nhk_sports",
        labelResId = R.string.news_feed_nhk_sports,
        url = "https://www3.nhk.or.jp/rss/news/cat7.xml",
        category = NewsFeedCategory.Sports,
        source = NewsFeedSource.Nhk
    )

    val NHK_CULTURE = BundledFeed(
        id = "nhk_culture",
        labelResId = R.string.news_feed_nhk_culture,
        url = "https://www3.nhk.or.jp/rss/news/cat5.xml",
        category = NewsFeedCategory.Culture,
        source = NewsFeedSource.Nhk
    )

    val BBC_TOP = BundledFeed(
        id = "bbc_top",
        labelResId = R.string.news_feed_bbc_top,
        url = "https://feeds.bbci.co.uk/news/rss.xml",
        category = NewsFeedCategory.General,
        source = NewsFeedSource.Bbc
    )

    val BBC_WORLD = BundledFeed(
        id = "bbc_world",
        labelResId = R.string.news_feed_bbc_world,
        url = "https://feeds.bbci.co.uk/news/world/rss.xml",
        category = NewsFeedCategory.World,
        source = NewsFeedSource.Bbc
    )

    val BBC_TECHNOLOGY = BundledFeed(
        id = "bbc_technology",
        labelResId = R.string.news_feed_bbc_technology,
        url = "https://feeds.bbci.co.uk/news/technology/rss.xml",
        category = NewsFeedCategory.Technology,
        source = NewsFeedSource.Bbc
    )

    val BBC_BUSINESS = BundledFeed(
        id = "bbc_business",
        labelResId = R.string.news_feed_bbc_business,
        url = "https://feeds.bbci.co.uk/news/business/rss.xml",
        category = NewsFeedCategory.Business,
        source = NewsFeedSource.Bbc
    )

    val HACKER_NEWS = BundledFeed(
        id = "hackernews",
        labelResId = R.string.news_feed_hackernews,
        url = "https://news.ycombinator.com/rss",
        category = NewsFeedCategory.Tech,
        source = NewsFeedSource.Other
    )

    /**
     * Full catalog rendered by the picker, in display order. `ALL` is
     * exposed rather than individual feeds so the picker can iterate
     * without knowing every constant name, and so [NewsToolExecutor]
     * can derive its legacy `DEFAULT_FEEDS` list from the same source
     * of truth.
     */
    val ALL: List<BundledFeed> = listOf(
        NHK_GENERAL,
        NHK_SOCIETY,
        NHK_POLITICS,
        NHK_ECONOMY,
        NHK_WORLD,
        NHK_SPORTS,
        NHK_CULTURE,
        NHK_SCIENCE,
        BBC_TOP,
        BBC_WORLD,
        BBC_TECHNOLOGY,
        BBC_BUSINESS,
        HACKER_NEWS
    )

    /**
     * Map of legacy `source=` identifier → feed URL, so existing
     * `get_news` callers (`source=bbc` / `source=nhk` / `source=hackernews`)
     * keep resolving after the catalog expanded. New IDs (e.g.
     * `nhk_society`) also resolve through [ALL].
     */
    val LEGACY_ALIASES: Map<String, String> = mapOf(
        "bbc" to BBC_TOP.url,
        "nhk" to NHK_GENERAL.url,
        "hackernews" to HACKER_NEWS.url
    )

    /** Find a feed by its stable [BundledFeed.id] — null if unknown. */
    fun byId(id: String): BundledFeed? =
        ALL.firstOrNull { it.id == id }

    /** Find a feed by its RSS URL — null if the URL is a custom one. */
    fun byUrl(url: String): BundledFeed? =
        ALL.firstOrNull { it.url == url }
}
