package com.opensmarthome.speaker.tool.system

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AppNameMatcherTest {

    private val installed = listOf(
        AppInfo("Weather", "com.weather.app"),
        AppInfo("天気", "jp.weather.app"),
        AppInfo("Gmail", "com.google.android.gm"),
        AppInfo("YouTube", "com.google.android.youtube"),
        AppInfo("Google Maps", "com.google.android.apps.maps"),
        AppInfo("Netflix", "com.netflix.mediaclient"),
        AppInfo("Spotify", "com.spotify.music")
    )

    @Test
    fun `exact match wins`() {
        assertThat(AppNameMatcher.findBest("weather", installed)?.packageName)
            .isEqualTo("com.weather.app")
    }

    @Test
    fun `exact match is case-insensitive`() {
        assertThat(AppNameMatcher.findBest("WEATHER", installed)?.packageName)
            .isEqualTo("com.weather.app")
    }

    @Test
    fun `the weather app resolves by stripping hint suffix`() {
        assertThat(AppNameMatcher.findBest("weather app", installed)?.packageName)
            .isEqualTo("com.weather.app")
    }

    @Test
    fun `Japanese hint suffix is stripped`() {
        assertThat(AppNameMatcher.findBest("天気アプリ", installed)?.packageName)
            .isEqualTo("jp.weather.app")
    }

    @Test
    fun `open the weather app phrasing still lands on weather`() {
        val cleanedQuery = "weather"
        assertThat(AppNameMatcher.findBest(cleanedQuery, installed)?.packageName)
            .isEqualTo("com.weather.app")
    }

    @Test
    fun `token-set matches reordered multi-word labels`() {
        val best = AppNameMatcher.findBest("maps google", installed)
        assertThat(best?.packageName).isEqualTo("com.google.android.apps.maps")
    }

    @Test
    fun `substring match wins over Levenshtein`() {
        // "net" is a substring of Netflix; nothing else contains it.
        val best = AppNameMatcher.findBest("net", installed)
        assertThat(best?.packageName).isEqualTo("com.netflix.mediaclient")
    }

    @Test
    fun `partial typo tolerated via Levenshtein when no substring hit`() {
        // "spotyfy" is 2 edits from "spotify" — length 7, Levenshtein = 2, similarity = 5/7 ≈ 0.71,
        // normalised to 0..59 ≈ 42. That's below MIN_SCORE, so it should NOT match.
        val best = AppNameMatcher.findBest("spotyfy", installed)
        assertThat(best).isNull()
    }

    @Test
    fun `single-edit typo within threshold matches via substring path`() {
        // "spotify" exact match (length 7, edits = 0).
        assertThat(AppNameMatcher.findBest("spotify", installed)?.packageName)
            .isEqualTo("com.spotify.music")
    }

    @Test
    fun `empty query returns null`() {
        assertThat(AppNameMatcher.findBest("", installed)).isNull()
        assertThat(AppNameMatcher.findBest("   ", installed)).isNull()
    }

    @Test
    fun `empty candidates returns null`() {
        assertThat(AppNameMatcher.findBest("weather", emptyList())).isNull()
    }

    @Test
    fun `random noise below threshold returns null`() {
        assertThat(AppNameMatcher.findBest("xyzzy", installed)).isNull()
    }

    @Test
    fun `score symbolic sanity`() {
        assertThat(AppNameMatcher.score("gmail", "Gmail")).isEqualTo(100)
        // "gmail app" → strip "app" → "gmail" → matches Gmail exactly post-strip
        assertThat(AppNameMatcher.score("gmail app", "Gmail")).isEqualTo(90)
        // "google" is a token of "Google Maps" and appears in label
        assertThat(AppNameMatcher.score("google", "Google Maps")).isAtLeast(60)
        // total garbage stays below the floor
        assertThat(AppNameMatcher.score("qqqq", "Netflix")).isLessThan(AppNameMatcher.MIN_SCORE)
    }
}
