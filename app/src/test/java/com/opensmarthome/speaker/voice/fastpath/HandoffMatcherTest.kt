package com.opensmarthome.speaker.voice.fastpath

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HandoffMatcherTest {

    private fun match(s: String): FastPathMatch? =
        HandoffMatcher.tryMatch(s.lowercase().trim())

    @Test
    fun `english move this to kitchen speaker captures kitchen`() {
        val r = match("move this to the kitchen speaker")
        assertThat(r?.toolName).isEqualTo("handoff_session")
        assertThat(r?.arguments?.get("target")).isEqualTo("kitchen")
    }

    @Test
    fun `english send to bedroom captures bedroom`() {
        val r = match("send to bedroom")
        assertThat(r?.toolName).isEqualTo("handoff_session")
        assertThat(r?.arguments?.get("target")).isEqualTo("bedroom")
    }

    @Test
    fun `english move the conversation to living room captures living room`() {
        val r = match("move the conversation to living room")
        assertThat(r?.arguments?.get("target")).isEqualTo("living room")
    }

    @Test
    fun `english handoff to kitchen captures kitchen`() {
        val r = match("handoff to kitchen")
        assertThat(r?.toolName).isEqualTo("handoff_session")
        assertThat(r?.arguments?.get("target")).isEqualTo("kitchen")
    }

    @Test
    fun `english hand this off to bedroom captures bedroom`() {
        val r = match("hand this off to bedroom")
        assertThat(r?.arguments?.get("target")).isEqualTo("bedroom")
    }

    @Test
    fun `english full service name passes through unchanged`() {
        val r = match("move this to speaker-kitchen")
        assertThat(r?.arguments?.get("target")).isEqualTo("speaker-kitchen")
    }

    @Test
    fun `japanese にハンドオフ matches and captures peer name`() {
        val r = match("キッチンにハンドオフ")
        assertThat(r?.toolName).isEqualTo("handoff_session")
        assertThat(r?.arguments?.get("target")).isEqualTo("キッチン")
    }

    @Test
    fun `japanese に移して matches`() {
        val r = match("寝室に移して")
        assertThat(r?.toolName).isEqualTo("handoff_session")
        assertThat(r?.arguments?.get("target")).isEqualTo("寝室")
    }

    @Test
    fun `japanese に送って matches`() {
        val r = match("リビングに送って")
        assertThat(r?.arguments?.get("target")).isEqualTo("リビング")
    }

    @Test
    fun `match sets spoken confirmation naming the target`() {
        val r = match("move this to the kitchen speaker")
        assertThat(r?.spokenConfirmation).isNotNull()
        assertThat(r?.spokenConfirmation).contains("kitchen")
    }

    @Test
    fun `unrelated utterances do not match`() {
        assertThat(match("what time is it")).isNull()
        assertThat(match("set a timer for five minutes")).isNull()
        assertThat(match("turn off the lights")).isNull()
    }
}
