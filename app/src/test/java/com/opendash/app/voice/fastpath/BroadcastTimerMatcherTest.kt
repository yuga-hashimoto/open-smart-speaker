package com.opendash.app.voice.fastpath

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BroadcastTimerMatcherTest {

    private fun match(s: String): FastPathMatch? =
        BroadcastTimerMatcher.tryMatch(s.lowercase().trim())

    @Test
    fun `english set N minute timer on all speakers captures broadcast_timer with seconds`() {
        val r = match("set a 5 minute timer on all speakers")
        assertThat(r?.toolName).isEqualTo("broadcast_timer")
        assertThat(r?.arguments?.get("seconds")).isEqualTo(300.0)
    }

    @Test
    fun `english on every speaker variant also matches`() {
        val r = match("set a 10 minute timer on every speaker")
        assertThat(r?.toolName).isEqualTo("broadcast_timer")
        assertThat(r?.arguments?.get("seconds")).isEqualTo(600.0)
    }

    @Test
    fun `english second unit matches`() {
        val r = match("set 30 seconds timer on all speakers")
        assertThat(r?.toolName).isEqualTo("broadcast_timer")
        assertThat(r?.arguments?.get("seconds")).isEqualTo(30.0)
    }

    @Test
    fun `english hour unit matches`() {
        val r = match("set a 2 hour timer on all speakers")
        assertThat(r?.toolName).isEqualTo("broadcast_timer")
        assertThat(r?.arguments?.get("seconds")).isEqualTo(7200.0)
    }

    @Test
    fun `japanese 全スピーカー minute pattern captures seconds`() {
        val r = BroadcastTimerMatcher.tryMatch("全スピーカーで5分タイマー")
        assertThat(r?.toolName).isEqualTo("broadcast_timer")
        assertThat(r?.arguments?.get("seconds")).isEqualTo(300.0)
    }

    @Test
    fun `japanese 全スピーカー に variant captures seconds`() {
        val r = BroadcastTimerMatcher.tryMatch("全スピーカーに10分のタイマー")
        assertThat(r?.toolName).isEqualTo("broadcast_timer")
        assertThat(r?.arguments?.get("seconds")).isEqualTo(600.0)
    }

    @Test
    fun `japanese second unit matches`() {
        val r = BroadcastTimerMatcher.tryMatch("全スピーカーで30秒タイマー")
        assertThat(r?.arguments?.get("seconds")).isEqualTo(30.0)
    }

    @Test
    fun `plain set timer 5 minutes does NOT match broadcast_timer`() {
        // Plain local timer utterance — TimerMatcher handles this, not us.
        val r = match("set timer 5 minutes")
        assertThat(r).isNull()
    }

    @Test
    fun `plain japanese 5 minute timer does NOT match broadcast_timer`() {
        // Plain local timer utterance — TimerMatcher handles this, not us.
        val r = BroadcastTimerMatcher.tryMatch("5分タイマー")
        assertThat(r).isNull()
    }

    @Test
    fun `unrelated utterance returns null`() {
        assertThat(match("turn on the lights")).isNull()
        assertThat(match("broadcast dinner is ready to all speakers")).isNull()
    }
}
