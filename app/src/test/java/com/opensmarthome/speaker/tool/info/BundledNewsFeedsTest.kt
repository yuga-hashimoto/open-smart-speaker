package com.opensmarthome.speaker.tool.info

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Sanity check the bundled feed catalog. Catches three classes of bug
 * that otherwise only surface on a device:
 *
 *  - Duplicate `id` (collapses entries in the picker → one row steals
 *    selection events for the other).
 *  - Duplicate `url` (the Home dashboard reverse-lookup (`byUrl`) picks
 *    the wrong bundled feed).
 *  - Malformed URL (the RSS provider fails with `MalformedURLException`
 *    and the whole headlines card goes blank with no diagnostic).
 */
class BundledNewsFeedsTest {

    @Test
    fun `catalog is not empty`() {
        assertThat(BundledNewsFeeds.ALL).isNotEmpty()
    }

    @Test
    fun `all ids are unique`() {
        val ids = BundledNewsFeeds.ALL.map { it.id }
        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun `all urls are unique`() {
        val urls = BundledNewsFeeds.ALL.map { it.url }
        assertThat(urls).containsNoDuplicates()
    }

    @Test
    fun `all urls are https`() {
        BundledNewsFeeds.ALL.forEach { feed ->
            assertThat(feed.url).startsWith("https://")
        }
    }

    @Test
    fun `nhk general preserves legacy url for backward compat`() {
        // Pre-picker installs have been hitting this exact URL — if we
        // ever change it, users upgrade and silently see a different feed.
        assertThat(BundledNewsFeeds.NHK_GENERAL.url)
            .isEqualTo("https://www3.nhk.or.jp/rss/news/cat0.xml")
    }

    @Test
    fun `bbc top preserves legacy url for backward compat`() {
        assertThat(BundledNewsFeeds.BBC_TOP.url)
            .isEqualTo("https://feeds.bbci.co.uk/news/rss.xml")
    }

    @Test
    fun `hackernews preserves legacy url for backward compat`() {
        assertThat(BundledNewsFeeds.HACKER_NEWS.url)
            .isEqualTo("https://news.ycombinator.com/rss")
    }

    @Test
    fun `byId returns known feed and null for unknown`() {
        assertThat(BundledNewsFeeds.byId("nhk_general"))
            .isEqualTo(BundledNewsFeeds.NHK_GENERAL)
        assertThat(BundledNewsFeeds.byId("not-a-real-feed")).isNull()
    }

    @Test
    fun `byUrl returns known feed and null for custom`() {
        assertThat(BundledNewsFeeds.byUrl(BundledNewsFeeds.BBC_TOP.url))
            .isEqualTo(BundledNewsFeeds.BBC_TOP)
        assertThat(BundledNewsFeeds.byUrl("https://example.com/custom.rss")).isNull()
    }

    @Test
    fun `legacy aliases map to bundled feeds`() {
        assertThat(BundledNewsFeeds.LEGACY_ALIASES["bbc"])
            .isEqualTo(BundledNewsFeeds.BBC_TOP.url)
        assertThat(BundledNewsFeeds.LEGACY_ALIASES["nhk"])
            .isEqualTo(BundledNewsFeeds.NHK_GENERAL.url)
        assertThat(BundledNewsFeeds.LEGACY_ALIASES["hackernews"])
            .isEqualTo(BundledNewsFeeds.HACKER_NEWS.url)
    }

    @Test
    fun `catalog contains expected sources`() {
        val sources = BundledNewsFeeds.ALL.map { it.source }.toSet()
        assertThat(sources).containsExactly(
            NewsFeedSource.Nhk,
            NewsFeedSource.Bbc,
            NewsFeedSource.Other
        )
    }

    @Test
    fun `nhk feed count covers all documented categories`() {
        // We promised General / Society / Science / Politics / Economy
        // / World / Sports / Culture in the plan — all 8 must ship.
        val nhk = BundledNewsFeeds.ALL.filter { it.source == NewsFeedSource.Nhk }
        assertThat(nhk).hasSize(8)
    }
}
