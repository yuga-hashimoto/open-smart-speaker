package com.opendash.app.voice.fastpath

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BroadcastCancelTimerMatcherTest {

    private fun match(s: String): FastPathMatch? =
        BroadcastCancelTimerMatcher.tryMatch(s.lowercase().trim())

    @Test
    fun `english cancel timers on all speakers routes to broadcast_cancel_timer`() {
        val r = match("cancel timers on all speakers")
        assertThat(r?.toolName).isEqualTo("broadcast_cancel_timer")
    }

    @Test
    fun `english cancel all timers on every speaker routes to broadcast_cancel_timer`() {
        val r = match("cancel all timers on every speaker")
        assertThat(r?.toolName).isEqualTo("broadcast_cancel_timer")
    }

    @Test
    fun `english stop variant also matches`() {
        val r = match("stop all timers on every speaker")
        assertThat(r?.toolName).isEqualTo("broadcast_cancel_timer")
    }

    @Test
    fun `japanese 全スピーカーのタイマー キャンセル matches`() {
        val r = BroadcastCancelTimerMatcher.tryMatch("全スピーカーのタイマーをキャンセル")
        assertThat(r?.toolName).isEqualTo("broadcast_cancel_timer")
    }

    @Test
    fun `japanese 全スピーカーのタイマー 取り消し matches`() {
        val r = BroadcastCancelTimerMatcher.tryMatch("全スピーカーのタイマーを取り消し")
        assertThat(r?.toolName).isEqualTo("broadcast_cancel_timer")
    }

    @Test
    fun `japanese 全スピーカーのタイマー 止めて matches`() {
        val r = BroadcastCancelTimerMatcher.tryMatch("全スピーカーのタイマー止めて")
        assertThat(r?.toolName).isEqualTo("broadcast_cancel_timer")
    }

    @Test
    fun `plain english cancel all timers does NOT match multi-room`() {
        // Plain single-device utterance — CancelAllTimersMatcher handles this.
        val r = match("cancel all timers")
        assertThat(r).isNull()
    }

    @Test
    fun `plain japanese タイマー全部止めて does NOT match multi-room`() {
        // Plain single-device utterance — CancelAllTimersMatcher handles this.
        val r = BroadcastCancelTimerMatcher.tryMatch("タイマー全部止めて")
        assertThat(r).isNull()
    }

    @Test
    fun `unrelated utterance returns null`() {
        assertThat(match("turn on the lights")).isNull()
        assertThat(match("set a 5 minute timer on all speakers")).isNull()
    }
}
