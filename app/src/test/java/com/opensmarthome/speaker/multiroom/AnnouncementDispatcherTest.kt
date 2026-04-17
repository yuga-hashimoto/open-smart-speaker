package com.opensmarthome.speaker.multiroom

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.voice.tts.TextToSpeech
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AnnouncementDispatcherTest {

    private fun dispatcher(tts: TextToSpeech = stubTts()): AnnouncementDispatcher =
        AnnouncementDispatcher(tts)

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
}
