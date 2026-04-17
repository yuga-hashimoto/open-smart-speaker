package com.opensmarthome.speaker.voice.fastpath

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OpenUrlMatcherTest {

    @Test
    fun `explicit https url captured`() {
        val m = OpenUrlMatcher.tryMatch("open https://example.com/path")
        assertThat(m?.toolName).isEqualTo("open_url")
        assertThat(m?.arguments).containsEntry("url", "https://example.com/path")
    }

    @Test
    fun `explicit http url captured`() {
        val m = OpenUrlMatcher.tryMatch("visit http://news.example.org and tell me more")
        assertThat(m?.toolName).isEqualTo("open_url")
        assertThat(m?.arguments?.get("url") as String).startsWith("http://news.example.org")
    }

    @Test
    fun `short open example com prepends https`() {
        val m = OpenUrlMatcher.tryMatch("open example.com")
        assertThat(m?.toolName).isEqualTo("open_url")
        assertThat(m?.arguments).containsEntry("url", "https://example.com")
    }

    @Test
    fun `open the website example com prepends https`() {
        val m = OpenUrlMatcher.tryMatch("open the website example.com")
        assertThat(m?.toolName).isEqualTo("open_url")
        assertThat(m?.arguments).containsEntry("url", "https://example.com")
    }

    @Test
    fun `open site news example org`() {
        val m = OpenUrlMatcher.tryMatch("open site news.example.org")
        assertThat(m?.toolName).isEqualTo("open_url")
        assertThat(m?.arguments).containsEntry("url", "https://news.example.org")
    }

    @Test
    fun `unrelated utterance does not match`() {
        assertThat(OpenUrlMatcher.tryMatch("set a timer for 5 minutes")).isNull()
    }

    @Test
    fun `plain word open does not match`() {
        // "open" without a domain / URL must not trigger.
        assertThat(OpenUrlMatcher.tryMatch("open the door")).isNull()
    }

    @Test
    fun `javascript scheme is not captured by matcher`() {
        // The matcher regex is anchored to http/https, so javascript: is ignored.
        assertThat(OpenUrlMatcher.tryMatch("open javascript:alert(1)")).isNull()
    }

    @Test
    fun `trailing punctuation stripped from url`() {
        val m = OpenUrlMatcher.tryMatch("check https://example.com/page.")
        assertThat(m?.arguments).containsEntry("url", "https://example.com/page")
    }

    @Test
    fun `router places open url matcher before launch app for explicit url`() {
        val router = DefaultFastPathRouter()
        val match = router.match("open https://example.com")
        assertThat(match?.toolName).isEqualTo("open_url")
    }

    @Test
    fun `router places open url matcher before launch app for bare domain`() {
        val router = DefaultFastPathRouter()
        val match = router.match("open example.com")
        assertThat(match?.toolName).isEqualTo("open_url")
        assertThat(match?.arguments).containsEntry("url", "https://example.com")
    }
}
