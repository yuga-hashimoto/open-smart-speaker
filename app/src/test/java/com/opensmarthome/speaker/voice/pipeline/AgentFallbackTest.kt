package com.opensmarthome.speaker.voice.pipeline

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AgentFallbackTest {

    @Test
    fun `japanese fallback is spoken in japanese`() {
        val msg = AgentFallback.roundCapMessage("ja-JP")
        assertThat(msg).contains("時間")
    }

    @Test
    fun `english fallback is spoken in english`() {
        val msg = AgentFallback.roundCapMessage("en-US")
        assertThat(msg.lowercase()).contains("taking")
    }

    @Test
    fun `null language defaults to english`() {
        val msg = AgentFallback.roundCapMessage(null)
        assertThat(msg.lowercase()).contains("taking")
    }

    @Test
    fun `unknown language defaults to english`() {
        val msg = AgentFallback.roundCapMessage("xx")
        assertThat(msg.lowercase()).contains("taking")
    }

    @Test
    fun `fallback phrases are non-empty`() {
        assertThat(AgentFallback.roundCapMessage("ja")).isNotEmpty()
        assertThat(AgentFallback.roundCapMessage("en")).isNotEmpty()
    }
}
