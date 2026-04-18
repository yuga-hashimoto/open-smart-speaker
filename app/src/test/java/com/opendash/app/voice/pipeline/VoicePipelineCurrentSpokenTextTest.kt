package com.opendash.app.voice.pipeline

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.opendash.app.assistant.provider.AssistantProvider
import com.opendash.app.assistant.router.ConversationRouter
import com.opendash.app.assistant.router.RoutingPolicy
import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.voice.stt.SpeechToText
import com.opendash.app.voice.tts.TextToSpeech
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Verifies [VoicePipeline.currentSpokenText] forwards the active TTS
 * provider's [TextToSpeech.currentChunk]. The UI uses this to render
 * only the sentence currently being spoken (karaoke-style rolling).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoicePipelineCurrentSpokenTextTest {

    private val testDispatcher = StandardTestDispatcher()

    private class ChunkedFakeTts : TextToSpeech {
        private val _chunk = MutableStateFlow("")
        private val _speaking = MutableStateFlow(false)
        override val isSpeaking: StateFlow<Boolean> = _speaking.asStateFlow()
        override val currentChunk: StateFlow<String> = _chunk.asStateFlow()
        override suspend fun speak(text: String) = Unit
        override fun stop() { _chunk.value = "" }

        fun emit(chunk: String) { _chunk.value = chunk }
    }

    private lateinit var pipeline: VoicePipeline
    private lateinit var fakeTts: ChunkedFakeTts

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = mockk<Context>(relaxed = true)
        val stt = mockk<SpeechToText>(relaxed = true)
        val router = mockk<ConversationRouter>(relaxed = true)
        val toolExecutor = mockk<ToolExecutor>(relaxed = true)
        val provider = mockk<AssistantProvider>(relaxed = true)
        val preferences = mockk<AppPreferences>(relaxed = true)
        fakeTts = ChunkedFakeTts()

        every { stt.isListening } returns MutableStateFlow(false)
        every { router.activeProvider } returns MutableStateFlow(provider)
        every { router.availableProviders } returns MutableStateFlow(listOf(provider))
        every { router.policy } returns MutableStateFlow(RoutingPolicy.Auto)
        val audioManager = mockk<android.media.AudioManager>(relaxed = true)
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        every { context.getSystemService(any<String>()) } returns audioManager
        every { preferences.observe<Boolean>(any()) } returns flowOf(null)
        every { preferences.observe<Long>(any()) } returns flowOf(null)
        every { preferences.observe<String>(any()) } returns flowOf(null)
        coEvery { toolExecutor.availableTools() } returns emptyList()

        pipeline = VoicePipeline(
            context = context,
            stt = stt,
            tts = fakeTts,
            router = router,
            toolExecutor = toolExecutor,
            moshi = Moshi.Builder().build(),
            preferences = preferences
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `currentSpokenText reflects the tts currentChunk`() {
        assertThat(pipeline.currentSpokenText.value).isEmpty()

        fakeTts.emit("First sentence spoken aloud.")
        assertThat(pipeline.currentSpokenText.value)
            .isEqualTo("First sentence spoken aloud.")

        fakeTts.emit("Second sentence now playing.")
        assertThat(pipeline.currentSpokenText.value)
            .isEqualTo("Second sentence now playing.")

        fakeTts.emit("")
        assertThat(pipeline.currentSpokenText.value).isEmpty()
    }
}
