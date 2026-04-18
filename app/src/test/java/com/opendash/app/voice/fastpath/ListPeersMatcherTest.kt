package com.opendash.app.voice.fastpath

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ListPeersMatcherTest {

    private fun match(s: String): FastPathMatch? = ListPeersMatcher.tryMatch(s.lowercase().trim())

    @Test
    fun `english list nearby speakers matches`() {
        assertThat(match("list nearby speakers")?.toolName).isEqualTo("list_peers")
    }

    @Test
    fun `english who's on the network matches`() {
        assertThat(match("who's on the network")?.toolName).isEqualTo("list_peers")
    }

    @Test
    fun `english what speakers are nearby matches`() {
        assertThat(match("what speakers are nearby")?.toolName).isEqualTo("list_peers")
    }

    @Test
    fun `japanese 近くのスピーカー matches`() {
        assertThat(match("近くのスピーカー")?.toolName).isEqualTo("list_peers")
    }

    @Test
    fun `japanese スピーカー一覧 matches`() {
        assertThat(match("スピーカー一覧")?.toolName).isEqualTo("list_peers")
    }

    @Test
    fun `japanese 周辺のスピーカー matches`() {
        assertThat(match("周辺のスピーカー")?.toolName).isEqualTo("list_peers")
    }

    @Test
    fun `unrelated utterances do not match`() {
        assertThat(match("broadcast dinner to all speakers")).isNull()
        assertThat(match("set a timer for five minutes")).isNull()
        assertThat(match("lock the screen")).isNull()
    }

    @Test
    fun `match is a tool-dispatch not speak-only`() {
        val r = match("list nearby speakers")
        assertThat(r?.toolName).isEqualTo("list_peers")
        assertThat(r?.spokenConfirmation).isNull()
    }
}
