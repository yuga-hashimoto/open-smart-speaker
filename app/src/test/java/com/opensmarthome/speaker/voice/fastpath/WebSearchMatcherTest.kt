package com.opensmarthome.speaker.voice.fastpath

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WebSearchMatcherTest {

    @Test
    fun `english search for extracts query`() {
        val m = WebSearchMatcher.tryMatch("search for kotlin coroutines")
        assertThat(m?.toolName).isEqualTo("web_search")
        assertThat(m?.arguments).containsEntry("query", "kotlin coroutines")
    }

    @Test
    fun `english look up extracts query`() {
        val m = WebSearchMatcher.tryMatch("look up the weather on mars")
        assertThat(m?.toolName).isEqualTo("web_search")
        assertThat(m?.arguments?.get("query")).isEqualTo("the weather on mars")
    }

    @Test
    fun `english google extracts query and strips trailing punctuation`() {
        val m = WebSearchMatcher.tryMatch("google jetpack compose tutorial.")
        assertThat(m?.toolName).isEqualTo("web_search")
        assertThat(m?.arguments?.get("query")).isEqualTo("jetpack compose tutorial")
    }

    @Test
    fun `japanese wo kensaku shite extracts query`() {
        val m = WebSearchMatcher.tryMatch("kotlinを検索して")
        assertThat(m?.toolName).isEqualTo("web_search")
        assertThat(m?.arguments?.get("query")).isEqualTo("kotlin")
    }

    @Test
    fun `japanese wo guguru extracts query`() {
        val m = WebSearchMatcher.tryMatch("東京の天気をググって")
        assertThat(m?.toolName).isEqualTo("web_search")
        assertThat(m?.arguments?.get("query")).isEqualTo("東京の天気")
    }

    @Test
    fun `japanese ni tsuite shirabete extracts query`() {
        val m = WebSearchMatcher.tryMatch("量子コンピュータについて調べて")
        assertThat(m?.toolName).isEqualTo("web_search")
        assertThat(m?.arguments?.get("query")).isEqualTo("量子コンピュータ")
    }

    @Test
    fun `english empty query prompts user instead of firing tool`() {
        val m = WebSearchMatcher.tryMatch("search")
        assertThat(m?.toolName).isNull()
        assertThat(m?.spokenConfirmation).isNotNull()
        assertThat(m?.spokenConfirmation).contains("search")
    }

    @Test
    fun `english bare web search prompts user`() {
        val m = WebSearchMatcher.tryMatch("web search")
        assertThat(m?.toolName).isNull()
        assertThat(m?.spokenConfirmation).isNotNull()
    }

    @Test
    fun `japanese bare web検索 prompts user`() {
        val m = WebSearchMatcher.tryMatch("web検索して")
        assertThat(m?.toolName).isNull()
        assertThat(m?.spokenConfirmation).contains("検索")
    }

    @Test
    fun `unrelated utterance returns null`() {
        assertThat(WebSearchMatcher.tryMatch("turn on the kitchen lights")).isNull()
        assertThat(WebSearchMatcher.tryMatch("set a timer for 5 minutes")).isNull()
    }
}
