package com.opendash.app.voice.fastpath

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Every test passes a pre-normalised (lowercased + trimmed) string to match
 * the router contract in [DefaultFastPathRouter.match]. Japanese is fed
 * directly because lowercasing is a no-op for CJK text.
 */
class BroadcastGroupMatcherTest {

    private fun match(s: String): FastPathMatch? =
        BroadcastGroupMatcher.tryMatch(s.lowercase().trim())

    @Test
    fun `broadcast X to kitchen captures text + group`() {
        val r = match("broadcast dinner is ready to kitchen")
        assertThat(r?.toolName).isEqualTo("broadcast_tts")
        assertThat(r?.arguments?.get("text")).isEqualTo("dinner is ready")
        assertThat(r?.arguments?.get("group")).isEqualTo("kitchen")
        assertThat(r?.arguments?.get("language")).isEqualTo("en")
    }

    @Test
    fun `announce X to the upstairs speakers strips suffix`() {
        val r = match("announce movie starting to the upstairs speakers")
        assertThat(r?.arguments?.get("text")).isEqualTo("movie starting")
        assertThat(r?.arguments?.get("group")).isEqualTo("upstairs")
    }

    @Test
    fun `tell the bedroom speakers X form captures group first`() {
        val r = match("tell the bedroom speakers lights are out")
        assertThat(r?.arguments?.get("text")).isEqualTo("lights are out")
        assertThat(r?.arguments?.get("group")).isEqualTo("bedroom")
    }

    @Test
    fun `broadcast to everyone does not match — leave that for BroadcastTtsMatcher`() {
        // Group matcher must defer to BroadcastTtsMatcher when the "group"
        // token is really an "everyone" synonym — otherwise we'd route
        // "to all" through the group-lookup path and hit a bogus "unknown
        // group: all" failure instead of fanning out to every peer.
        assertThat(match("broadcast hello to all")).isNull()
        assertThat(match("broadcast hello to everyone")).isNull()
        assertThat(match("broadcast hello to all speakers")).isNull()
    }

    @Test
    fun `japanese キッチンに X ってアナウンス captures`() {
        val r = BroadcastGroupMatcher.tryMatch("キッチンにご飯だよってアナウンス")
        assertThat(r?.toolName).isEqualTo("broadcast_tts")
        assertThat(r?.arguments?.get("text")).isEqualTo("ご飯だよ")
        assertThat(r?.arguments?.get("group")).isEqualTo("キッチン")
        assertThat(r?.arguments?.get("language")).isEqualTo("ja")
    }

    @Test
    fun `japanese X って寝室グループに伝えて captures`() {
        val r = BroadcastGroupMatcher.tryMatch("おやすみって寝室グループに伝えて")
        assertThat(r?.arguments?.get("text")).isEqualTo("おやすみ")
        assertThat(r?.arguments?.get("group")).isEqualTo("寝室")
    }

    @Test
    fun `japanese 全員 defers to BroadcastTtsMatcher`() {
        // 全員/みんな/全スピーカー are owned by BroadcastTtsMatcher.
        assertThat(BroadcastGroupMatcher.tryMatch("全員にこんにちはってアナウンス")).isNull()
    }

    @Test
    fun `unrelated utterances do not match`() {
        assertThat(match("what time is it")).isNull()
        assertThat(match("set a timer for five minutes")).isNull()
        assertThat(match("open the kitchen")).isNull()
    }

    @Test
    fun `bare broadcast without message does not match`() {
        assertThat(match("broadcast to kitchen")).isNull()
    }

    @Test
    fun `group matcher beats BroadcastTtsMatcher when wired into default router`() {
        // Sanity check: the default router composes group before all.
        val router = DefaultFastPathRouter()
        val r = router.match("broadcast dinner to kitchen")
        assertThat(r?.toolName).isEqualTo("broadcast_tts")
        assertThat(r?.arguments?.get("group")).isEqualTo("kitchen")
    }
}
