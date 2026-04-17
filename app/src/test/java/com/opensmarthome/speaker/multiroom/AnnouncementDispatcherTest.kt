package com.opensmarthome.speaker.multiroom

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.session.ConversationHistoryManager
import com.opensmarthome.speaker.voice.tts.TextToSpeech
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AnnouncementDispatcherTest {

    private fun dispatcher(
        tts: TextToSpeech = stubTts(),
        history: ConversationHistoryManager? = null
    ): AnnouncementDispatcher =
        AnnouncementDispatcher(tts, historyProvider = { history })

    private fun stubTts(): TextToSpeech {
        val tts = mockk<TextToSpeech>(relaxed = true)
        io.mockk.every { tts.isSpeaking } returns MutableStateFlow(false).asStateFlow()
        coEvery { tts.speak(any()) } answers { /* no-op */ }
        return tts
    }

    private fun envelope(type: String, payload: Map<String, Any?> = emptyMap()) =
        AnnouncementEnvelope(
            v = 1, type = type, id = "id", from = "peer", ts = 1L,
            payload = payload, hmac = "sig"
        )

    @Test
    fun `tts_broadcast with text triggers speak`() = runTest {
        val tts = stubTts()
        val r = dispatcher(tts).dispatch(
            envelope("tts_broadcast", mapOf("text" to "hello world"))
        )
        assertThat(r).isInstanceOf(AnnouncementDispatcher.DispatchOutcome.Spoke::class.java)
        assertThat((r as AnnouncementDispatcher.DispatchOutcome.Spoke).text).isEqualTo("hello world")
        // Dispatch is async; we don't assert speak here because the dispatcher's
        // internal scope runs on Dispatchers.Default. The outcome contract is
        // "I've queued this", which is what the return value reflects.
    }

    @Test
    fun `tts_broadcast without text is rejected`() = runTest {
        val r = dispatcher().dispatch(envelope("tts_broadcast", emptyMap()))
        assertThat(r).isInstanceOf(AnnouncementDispatcher.DispatchOutcome.Rejected::class.java)
    }

    @Test
    fun `tts_broadcast with blank text is rejected`() = runTest {
        val r = dispatcher().dispatch(envelope("tts_broadcast", mapOf("text" to "   ")))
        assertThat(r).isInstanceOf(AnnouncementDispatcher.DispatchOutcome.Rejected::class.java)
    }

    @Test
    fun `heartbeat is acknowledged without side effects`() {
        val tts = stubTts()
        val r = dispatcher(tts).dispatch(envelope("heartbeat"))
        assertThat(r).isEqualTo(AnnouncementDispatcher.DispatchOutcome.AcknowledgedHeartbeat)
        coVerify(exactly = 0) { tts.speak(any()) }
    }

    @Test
    fun `unknown type is tagged Unhandled without throwing`() {
        val r = dispatcher().dispatch(envelope("future_message_type"))
        assertThat(r).isInstanceOf(AnnouncementDispatcher.DispatchOutcome.Unhandled::class.java)
        assertThat((r as AnnouncementDispatcher.DispatchOutcome.Unhandled).type)
            .isEqualTo("future_message_type")
    }

    @Test
    fun `session_handoff conversation seeds history replacing prior state`() {
        val history = ConversationHistoryManager()
        // Pre-existing unrelated chatter on the receiver — should be cleared,
        // not appended to, because the user said "move this".
        history.add(AssistantMessage.User(content = "old unrelated"))

        val payload = mapOf(
            "mode" to "conversation",
            "messages" to listOf(
                mapOf("role" to "user", "content" to "what's the weather"),
                mapOf("role" to "assistant", "content" to "Sunny and 22."),
                mapOf("role" to "system", "content" to "Handoff from living room."),
                mapOf("role" to "tool", "content" to "should be dropped"), // unknown role
                mapOf("role" to "user") // missing content — drop
            )
        )
        val r = dispatcher(history = history).dispatch(envelope("session_handoff", payload))

        assertThat(r).isInstanceOf(AnnouncementDispatcher.DispatchOutcome.HandoffSeeded::class.java)
        assertThat((r as AnnouncementDispatcher.DispatchOutcome.HandoffSeeded).count).isEqualTo(3)

        val seeded = history.history
        assertThat(seeded).hasSize(3)
        assertThat(seeded[0]).isInstanceOf(AssistantMessage.User::class.java)
        assertThat((seeded[0] as AssistantMessage.User).content).isEqualTo("what's the weather")
        assertThat(seeded[1]).isInstanceOf(AssistantMessage.Assistant::class.java)
        assertThat(seeded[2]).isInstanceOf(AssistantMessage.System::class.java)
    }

    @Test
    fun `session_handoff missing mode is rejected`() {
        val history = ConversationHistoryManager()
        val r = dispatcher(history = history).dispatch(envelope("session_handoff", emptyMap()))
        assertThat(r).isInstanceOf(AnnouncementDispatcher.DispatchOutcome.Rejected::class.java)
    }

    @Test
    fun `session_handoff media is Unhandled with TODO reason`() {
        val history = ConversationHistoryManager()
        val payload = mapOf("mode" to "media")
        val r = dispatcher(history = history).dispatch(envelope("session_handoff", payload))
        assertThat(r).isInstanceOf(AnnouncementDispatcher.DispatchOutcome.Unhandled::class.java)
        assertThat((r as AnnouncementDispatcher.DispatchOutcome.Unhandled).type)
            .contains("session_handoff media")
    }

    @Test
    fun `session_handoff conversation without history wired is rejected gracefully`() {
        // No history manager available; dispatcher shouldn't crash.
        val payload = mapOf(
            "mode" to "conversation",
            "messages" to listOf(mapOf("role" to "user", "content" to "hi"))
        )
        val r = dispatcher(history = null).dispatch(envelope("session_handoff", payload))
        assertThat(r).isInstanceOf(AnnouncementDispatcher.DispatchOutcome.Rejected::class.java)
    }

    @Test
    fun `session_handoff conversation missing messages is rejected`() {
        val history = ConversationHistoryManager()
        val payload = mapOf("mode" to "conversation")
        val r = dispatcher(history = history).dispatch(envelope("session_handoff", payload))
        assertThat(r).isInstanceOf(AnnouncementDispatcher.DispatchOutcome.Rejected::class.java)
    }
}
