package com.opendash.app.voice.fastpath

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BroadcastTtsMatcherTest {

    private fun match(s: String): FastPathMatch? =
        BroadcastTtsMatcher.tryMatch(s.lowercase().trim())

    @Test
    fun `broadcast X to all speakers captures X`() {
        val r = match("broadcast dinner is ready to all speakers")
        assertThat(r?.toolName).isEqualTo("broadcast_tts")
        assertThat(r?.arguments?.get("text")).isEqualTo("dinner is ready")
        assertThat(r?.arguments?.get("language")).isEqualTo("en")
    }

    @Test
    fun `announce X to everyone captures X`() {
        val r = match("announce meeting in five minutes to everyone")
        assertThat(r?.arguments?.get("text")).isEqualTo("meeting in five minutes")
    }

    @Test
    fun `tell all speakers X captures X`() {
        val r = match("tell all speakers I'm heading home")
        assertThat(r?.arguments?.get("text")).isEqualTo("i'm heading home")
    }

    @Test
    fun `japanese 全スピーカーに pattern captures message`() {
        val r = BroadcastTtsMatcher.tryMatch("全スピーカーにアナウンスして: ご飯だよ")
        assertThat(r?.toolName).isEqualTo("broadcast_tts")
        assertThat(r?.arguments?.get("text")).isEqualTo("ご飯だよ")
        assertThat(r?.arguments?.get("language")).isEqualTo("ja")
    }

    @Test
    fun `japanese trailing keyword pattern captures message`() {
        val r = BroadcastTtsMatcher.tryMatch("ご飯だよって全員に伝えて")
        assertThat(r?.arguments?.get("text")).isEqualTo("ご飯だよ")
    }

    @Test
    fun `bare broadcast without a message does not match`() {
        assertThat(match("broadcast")).isNull()
        assertThat(match("announce")).isNull()
    }

    @Test
    fun `unrelated utterances do not match`() {
        assertThat(match("what time is it")).isNull()
        assertThat(match("set a timer for five minutes")).isNull()
        assertThat(match("lock the screen")).isNull()
    }
}
