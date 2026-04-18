package com.opensmarthome.speaker.assistant.provider.embedded

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ToolResultSummarizer]. The summarizer collapses raw tool
 * JSON (web_search, get_weather, get_forecast, get_news) into a compact
 * plain-text form so Gemma-class on-device models can reliably use it as
 * grounding in the 2nd agent-loop round without exhausting context or
 * emitting the bare "..." failure mode.
 */
class ToolResultSummarizerTest {

    private val summarizer = ToolResultSummarizer()

    // --- web_search ---

    @Test
    fun `web_search result is summarized with Abstract prefix`() {
        val raw = """{"query":"Webb telescope","abstract":"The James Webb Space Telescope is a space observatory.","source_url":null,"related":["infrared astronomy","NASA Goddard","Hubble successor"]}"""

        val summary = summarizer.summarize("web_search", raw)

        assertThat(summary).contains("Abstract:")
        assertThat(summary).contains("James Webb Space Telescope")
    }

    @Test
    fun `web_search summary is capped at max length`() {
        val longAbstract = "x".repeat(5000)
        val raw = """{"query":"q","abstract":"$longAbstract","source_url":null,"related":[]}"""

        val summary = summarizer.summarize("web_search", raw)

        assertThat(summary.length).isAtMost(ToolResultSummarizer.MAX_SUMMARY_CHARS)
    }

    @Test
    fun `web_search empty result falls back to friendly message`() {
        val raw = """{"query":"obscure","abstract":"","source_url":null,"related":[]}"""

        val summary = summarizer.summarize("web_search", raw)

        // Should never be blank — downstream LLM must have *something* to answer with.
        assertThat(summary).isNotEmpty()
    }

    // --- get_weather / get_forecast / get_news reuse FastPathResultFormatter ---

    @Test
    fun `get_weather result is summarized with Current prefix`() {
        val raw = """{"location":"Tokyo","temperature_c":18,"condition":"Clear","humidity":65,"wind_kph":12}"""

        val summary = summarizer.summarize("get_weather", raw)

        assertThat(summary).contains("Current:")
        assertThat(summary).contains("Tokyo")
    }

    @Test
    fun `get_forecast result is summarized day-by-day`() {
        val raw = """[{"date":"2026-04-17","min_c":10,"max_c":22,"condition":"Sunny"},{"date":"2026-04-18","min_c":8,"max_c":19,"condition":"Cloudy"}]"""

        val summary = summarizer.summarize("get_forecast", raw)

        assertThat(summary).contains("2026-04-17")
    }

    @Test
    fun `get_news result is summarized with Headlines prefix`() {
        val raw = """[{"title":"Headline A"},{"title":"Headline B"}]"""

        val summary = summarizer.summarize("get_news", raw)

        assertThat(summary).contains("Headlines:")
        assertThat(summary).contains("Headline A")
    }

    // --- unknown tools pass through raw (truncated) ---

    @Test
    fun `unknown tool returns raw data truncated`() {
        val raw = "some raw tool output"

        val summary = summarizer.summarize("set_timer", raw)

        assertThat(summary).isEqualTo("some raw tool output")
    }

    @Test
    fun `unknown tool with huge data is truncated`() {
        val raw = "x".repeat(ToolResultSummarizer.MAX_SUMMARY_CHARS + 500)

        val summary = summarizer.summarize("unknown_tool", raw)

        assertThat(summary.length).isAtMost(ToolResultSummarizer.MAX_SUMMARY_CHARS)
    }

    // --- blank input ---

    @Test
    fun `blank result returns empty marker`() {
        val summary = summarizer.summarize("web_search", "")

        assertThat(summary).isNotEmpty()
    }
}
