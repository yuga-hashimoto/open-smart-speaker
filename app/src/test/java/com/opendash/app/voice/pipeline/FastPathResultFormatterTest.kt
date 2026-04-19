package com.opendash.app.voice.pipeline

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FastPathResultFormatterTest {

    // --- get_weather ---

    @Test
    fun `weather json is spoken in english by default`() {
        val json =
            """{"location":"Tokyo","temperature_c":18.3,"condition":"Clear","humidity":65,"wind_kph":12.0}"""
        val spoken = FastPathResultFormatter.format("get_weather", json, ttsLanguageTag = null)
        assertThat(spoken).contains("Tokyo")
        assertThat(spoken).contains("18")
        // English template lowercases the condition for natural sentence flow.
        assertThat(spoken.lowercase()).contains("clear")
        assertThat(spoken).contains("65")
        assertThat(spoken).contains("12")
    }

    @Test
    fun `weather json is spoken in japanese for ja locale`() {
        val json =
            """{"location":"東京","temperature_c":18.0,"condition":"晴れ","humidity":60,"wind_kph":10.0}"""
        val spoken = FastPathResultFormatter.format("get_weather", json, ttsLanguageTag = "ja-JP")
        assertThat(spoken).contains("東京")
        assertThat(spoken).contains("18")
        assertThat(spoken).contains("晴れ")
        assertThat(spoken).contains("度")
    }

    @Test
    fun `weather with missing fields still renders a sentence`() {
        val json = """{"location":"Osaka","temperature_c":21.0,"condition":"Cloudy"}"""
        val spoken = FastPathResultFormatter.format("get_weather", json, ttsLanguageTag = "en-US")
        assertThat(spoken).contains("Osaka")
        assertThat(spoken).contains("21")
        assertThat(spoken.lowercase()).contains("cloudy")
    }

    // --- get_forecast ---

    @Test
    fun `forecast list spoken in english lists first day`() {
        val json =
            """[{"date":"2026-04-18","min_c":10.0,"max_c":19.0,"condition":"Sunny"},""" +
                """{"date":"2026-04-19","min_c":11.0,"max_c":20.0,"condition":"Cloudy"}]"""
        val spoken = FastPathResultFormatter.format("get_forecast", json, ttsLanguageTag = "en-GB")
        assertThat(spoken).contains("2026-04-18")
        assertThat(spoken.lowercase()).contains("sunny")
        assertThat(spoken).contains("10")
        assertThat(spoken).contains("19")
    }

    @Test
    fun `forecast list spoken in japanese`() {
        val json = """[{"date":"2026-04-18","min_c":10.0,"max_c":19.0,"condition":"晴れ"}]"""
        val spoken = FastPathResultFormatter.format("get_forecast", json, ttsLanguageTag = "ja")
        assertThat(spoken).contains("晴れ")
        assertThat(spoken).contains("10")
        assertThat(spoken).contains("19")
        assertThat(spoken).contains("度")
    }

    @Test
    fun `empty forecast array falls back to a graceful message`() {
        val spoken = FastPathResultFormatter.format("get_forecast", "[]", ttsLanguageTag = "en-US")
        assertThat(spoken).isNotEmpty()
    }

    // --- web_search ---

    @Test
    fun `web_search speaks abstract when available`() {
        val json =
            """{"query":"kotlin","abstract":"Kotlin is a modern JVM language.","source_url":"https://kotlinlang.org","related":["Compiler","JetBrains"]}"""
        val spoken = FastPathResultFormatter.format("web_search", json, ttsLanguageTag = "en-US")
        assertThat(spoken).contains("Kotlin is a modern JVM language.")
    }

    @Test
    fun `web_search falls back to first related when abstract is empty`() {
        val json =
            """{"query":"foo","abstract":"","source_url":null,"related":["Alpha topic","Beta topic"]}"""
        val spoken = FastPathResultFormatter.format("web_search", json, ttsLanguageTag = "en-US")
        assertThat(spoken).contains("Alpha topic")
    }

    @Test
    fun `web_search with nothing returns apologetic no-results phrase in japanese`() {
        val json = """{"query":"foo","abstract":"","source_url":null,"related":[]}"""
        val spoken = FastPathResultFormatter.format("web_search", json, ttsLanguageTag = "ja-JP")
        assertThat(spoken).isNotEmpty()
        // Should not return "Done."
        assertThat(spoken).isNotEqualTo("Done.")
    }

    @Test
    fun `web_search PR #421 results shape speaks top title and snippet in japanese`() {
        // Shape produced by DuckDuckGoHtmlSearchProvider once #421's
        // HTML-scrape exposes a `results` array on SearchResult.data.
        val json =
            """{"query":"LINEレンジャー","results":[""" +
                """{"title":"LINEレンジャー 公式サイト","url":"https://example.com/1","snippet":"LINEレンジャーは人気のパズルRPGです。"},""" +
                """{"title":"LINEレンジャー 攻略","url":"https://example.com/2","snippet":"攻略情報まとめ"}""" +
                """]}"""
        val spoken = FastPathResultFormatter.format("web_search", json, ttsLanguageTag = "ja-JP")

        assertThat(spoken).contains("LINEレンジャー")
        assertThat(spoken).contains("検索しました")
        assertThat(spoken).contains("トップの結果")
        assertThat(spoken).contains("LINEレンジャー 公式サイト")
        assertThat(spoken).contains("LINEレンジャーは人気のパズルRPGです。")
        // Must not be the apologetic fallback or the Done sentinel.
        assertThat(spoken).isNotEqualTo("Done.")
        assertThat(spoken).doesNotContain("見つかりませんでした")
    }

    @Test
    fun `web_search PR #421 results shape speaks top title and snippet in english`() {
        val json =
            """{"query":"kotlin language","results":[""" +
                """{"title":"Kotlin Programming Language","url":"https://kotlinlang.org","snippet":"Kotlin is a modern statically-typed language."},""" +
                """{"title":"Kotlin on Android","url":"https://developer.android.com","snippet":"Android's preferred language."}""" +
                """]}"""
        val spoken = FastPathResultFormatter.format("web_search", json, ttsLanguageTag = "en-US")

        assertThat(spoken).contains("kotlin language")
        assertThat(spoken.lowercase()).contains("top result")
        assertThat(spoken).contains("Kotlin Programming Language")
        assertThat(spoken).contains("Kotlin is a modern statically-typed language.")
    }

    @Test
    fun `web_search legacy abstract shape still works when results is absent`() {
        // Backwards-compat: the original DDG Instant Answer shape (pre-#421)
        // must still parse because DuckDuckGoSearchProvider is kept in the
        // search chain as a fallback.
        val json =
            """{"query":"kotlin","abstract":"Kotlin is a modern JVM language.","source_url":"https://kotlinlang.org","related":["Compiler","JetBrains"]}"""
        val spoken = FastPathResultFormatter.format("web_search", json, ttsLanguageTag = "en-US")
        assertThat(spoken).contains("Kotlin is a modern JVM language.")
    }

    @Test
    fun `web_search empty results array falls through to legacy abstract`() {
        // results:[] must not masquerade as a valid result — we fall back
        // to the abstract so the user still hears useful information.
        val json =
            """{"query":"foo","results":[],"abstract":"Legacy abstract here.","related":[]}"""
        val spoken = FastPathResultFormatter.format("web_search", json, ttsLanguageTag = "en-US")
        assertThat(spoken).contains("Legacy abstract here.")
    }

    // --- get_news ---

    @Test
    fun `news speaks first few headlines`() {
        val json =
            """[{"title":"First Story","summary":"s","link":"l","published":"p"},""" +
                """{"title":"Second Story","summary":"s","link":"l","published":"p"},""" +
                """{"title":"Third Story","summary":"s","link":"l","published":"p"}]"""
        val spoken = FastPathResultFormatter.format("get_news", json, ttsLanguageTag = "en-US")
        assertThat(spoken).contains("First Story")
        assertThat(spoken).contains("Second Story")
    }

    @Test
    fun `news empty array returns no-news phrase`() {
        val spoken = FastPathResultFormatter.format("get_news", "[]", ttsLanguageTag = "en-US")
        assertThat(spoken).isNotEmpty()
        assertThat(spoken).isNotEqualTo("Done.")
    }

    // --- other tools ---

    @Test
    fun `unknown tool falls back to generic Done`() {
        val spoken = FastPathResultFormatter.format("set_timer", "{}", ttsLanguageTag = "en-US")
        assertThat(spoken).isEqualTo("Done.")
    }

    @Test
    fun `malformed json falls back to generic Done`() {
        val spoken = FastPathResultFormatter.format("get_weather", "not-json", ttsLanguageTag = "en-US")
        assertThat(spoken).isEqualTo("Done.")
    }

    // --- Pre-summary (used by FastPathLlmPolisher as grounding facts) ---

    @Test
    fun `buildPolishSummary for weather contains Current prefix and fields`() {
        val json =
            """{"location":"Munakata","temperature_c":16.3,"condition":"Clear","humidity":65,"wind_kph":12.0}"""
        val summary = FastPathResultFormatter.buildPolishSummary("get_weather", json, "ja-JP")
        assertThat(summary).startsWith("Current:")
        assertThat(summary).contains("Munakata")
        // formatInt keeps the decimal when the value isn't an integer.
        assertThat(summary).contains("16.3°C")
        assertThat(summary).contains("Clear")
    }

    @Test
    fun `buildPolishSummary for forecast enumerates days`() {
        val json =
            """[{"date":"2026-04-18","min_c":12.0,"max_c":18.0,"condition":"Partly cloudy"},""" +
                """{"date":"2026-04-19","min_c":10.0,"max_c":16.0,"condition":"Clear"}]"""
        val summary = FastPathResultFormatter.buildPolishSummary("get_forecast", json, "en-US")
        assertThat(summary).contains("2026-04-18")
        assertThat(summary).contains("2026-04-19")
        assertThat(summary).contains("min 12°C")
        assertThat(summary).contains("max 18°C")
        assertThat(summary).contains("Partly cloudy")
    }

    @Test
    fun `buildPolishSummary for web_search surfaces abstract and related`() {
        val json =
            """{"query":"kotlin","abstract":"Kotlin is a modern JVM language.","source_url":null,"related":["Compiler","JetBrains"]}"""
        val summary = FastPathResultFormatter.buildPolishSummary("web_search", json, "en-US")
        assertThat(summary).contains("Abstract:")
        assertThat(summary).contains("Kotlin is a modern JVM language.")
        assertThat(summary).contains("Related:")
        assertThat(summary).contains("Compiler")
    }

    @Test
    fun `buildPolishSummary for empty web_search signals no results`() {
        val json = """{"query":"foo","abstract":"","source_url":null,"related":[]}"""
        val summary = FastPathResultFormatter.buildPolishSummary("web_search", json, "en-US")
        assertThat(summary).contains("No results")
    }

    @Test
    fun `buildPolishSummary for news lists numbered headlines`() {
        val json =
            """[{"title":"First Story"},{"title":"Second Story"},{"title":"Third Story"}]"""
        val summary = FastPathResultFormatter.buildPolishSummary("get_news", json, "en-US")
        assertThat(summary).startsWith("Headlines:")
        assertThat(summary).contains("1)")
        assertThat(summary).contains("First Story")
        assertThat(summary).contains("Second Story")
    }

    @Test
    fun `buildPolishSummary for unknown tool returns empty`() {
        val summary = FastPathResultFormatter.buildPolishSummary("set_timer", "{}", "en-US")
        assertThat(summary).isEmpty()
    }

    @Test
    fun `buildPolishSummary with blank data returns empty`() {
        val summary = FastPathResultFormatter.buildPolishSummary("get_weather", "", "en-US")
        assertThat(summary).isEmpty()
    }

    // --- get_location ---
    //
    // Regression: "現在地教えて" used to speak "Done." because get_location
    // was unhandled and fell through to the FALLBACK branch. The matcher
    // does fire (LocationMatcher → toolName=get_location), so the fix
    // belongs here in the formatter, not in the router.

    @Test
    fun `location json is spoken in japanese for ja locale`() {
        val json =
            """{"latitude":35.6812,"longitude":139.7671,"accuracy_m":12.0,""" +
                """"timestamp_ms":1713510000000,"provider":"network"}"""
        val spoken = FastPathResultFormatter.format("get_location", json, ttsLanguageTag = "ja-JP")
        assertThat(spoken).isNotEqualTo("Done.")
        assertThat(spoken).contains("現在地")
        assertThat(spoken).contains("35.68")
        assertThat(spoken).contains("139.77")
        assertThat(spoken).contains("12")
    }

    @Test
    fun `location json is spoken in english by default`() {
        val json = """{"latitude":-37.8136,"longitude":144.9631,"accuracy_m":8.0}"""
        val spoken = FastPathResultFormatter.format("get_location", json, ttsLanguageTag = null)
        assertThat(spoken).isNotEqualTo("Done.")
        assertThat(spoken.lowercase()).contains("latitude")
        assertThat(spoken).contains("-37.81")
        assertThat(spoken).contains("144.96")
        assertThat(spoken).contains("8")
    }

    @Test
    fun `location with missing accuracy still renders a sentence`() {
        val json = """{"latitude":35.68,"longitude":139.77}"""
        val spoken = FastPathResultFormatter.format("get_location", json, ttsLanguageTag = "ja-JP")
        assertThat(spoken).contains("35.68")
        assertThat(spoken).contains("139.77")
        assertThat(spoken).doesNotContain("精度")
    }

    @Test
    fun `location with blank data falls back to Done`() {
        val spoken = FastPathResultFormatter.format("get_location", "", ttsLanguageTag = "ja-JP")
        assertThat(spoken).isEqualTo("Done.")
    }

    @Test
    fun `location with missing coordinates falls back to Done`() {
        val spoken = FastPathResultFormatter.format(
            "get_location",
            """{"provider":"network"}""",
            ttsLanguageTag = "en-US"
        )
        assertThat(spoken).isEqualTo("Done.")
    }
}
