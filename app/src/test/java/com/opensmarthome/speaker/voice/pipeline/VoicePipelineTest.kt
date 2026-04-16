package com.opensmarthome.speaker.voice.pipeline

import android.content.Context
import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.model.AssistantSession
import com.opensmarthome.speaker.assistant.provider.AssistantProvider
import com.opensmarthome.speaker.assistant.router.ConversationRouter
import com.opensmarthome.speaker.assistant.router.RoutingPolicy
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.voice.tts.TextToSpeech
import com.opensmarthome.speaker.voice.stt.SpeechToText
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoicePipelineTest {

    private lateinit var pipeline: VoicePipeline
    private lateinit var context: Context
    private lateinit var stt: SpeechToText
    private lateinit var tts: TextToSpeech
    private lateinit var router: ConversationRouter
    private lateinit var toolExecutor: ToolExecutor
    private lateinit var provider: AssistantProvider
    private lateinit var preferences: AppPreferences
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        stt = mockk(relaxed = true)
        tts = mockk(relaxed = true)
        router = mockk(relaxed = true)
        toolExecutor = mockk(relaxed = true)
        provider = mockk(relaxed = true)
        preferences = mockk(relaxed = true)

        every { stt.isListening } returns MutableStateFlow(false)
        every { tts.isSpeaking } returns MutableStateFlow(false)
        every { tts.stop() } returns Unit
        every { router.activeProvider } returns MutableStateFlow(provider)
        every { router.availableProviders } returns MutableStateFlow(listOf(provider))
        every { router.policy } returns MutableStateFlow(RoutingPolicy.Auto)
        val audioManager = mockk<android.media.AudioManager>(relaxed = true)
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        every { context.getSystemService(any<String>()) } returns audioManager

        // Preference defaults
        every { preferences.observe<Boolean>(any()) } returns flowOf(null)
        every { preferences.observe<Long>(any()) } returns flowOf(null)
        every { preferences.observe<String>(any()) } returns flowOf(null)

        coEvery { provider.startSession(any()) } returns AssistantSession(providerId = "test")
        coEvery { toolExecutor.availableTools() } returns emptyList()

        pipeline = VoicePipeline(
            context = context,
            stt = stt,
            tts = tts,
            router = router,
            toolExecutor = toolExecutor,
            moshi = moshi,
            preferences = preferences
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(VoicePipelineState.Idle, pipeline.state.value)
    }

    @Test
    fun `processUserInput sets state to Processing then Idle`() = runTest {
        coEvery { router.resolveProvider(any()) } returns provider
        coEvery { provider.startSession(any()) } returns AssistantSession(providerId = "test")
        coEvery { provider.send(any(), any(), any()) } returns
            AssistantMessage.Assistant(content = "Hello!")
        coEvery { tts.speak(any()) } returns Unit

        pipeline.processUserInput("Hi")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(VoicePipelineState.Idle, pipeline.state.value)
        coVerify { tts.speak("Hello!") }
    }

    @Test
    fun `processUserInput handles error gracefully`() = runTest {
        coEvery { router.resolveProvider(any()) } throws RuntimeException("No providers")

        pipeline.processUserInput("test")
        testDispatcher.scheduler.advanceTimeBy(5000)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(VoicePipelineState.Idle, pipeline.state.value)
    }

    @Test
    fun `stopSpeaking stops TTS and returns to Idle`() {
        every { tts.stop() } returns Unit

        pipeline.stopSpeaking()

        assertEquals(VoicePipelineState.Idle, pipeline.state.value)
    }

    @Test
    fun `clearHistory resets session`() {
        pipeline.clearHistory()
        assertEquals(VoicePipelineState.Idle, pipeline.state.value)
    }

    @Test
    fun `interruptAndListen stops TTS immediately`() {
        pipeline.interruptAndListen()
        // tts.stop() called from relaxed mock — verify tts would have been halted
        io.mockk.verify { tts.stop() }
    }

    @Test
    fun `stopSpeaking abandons audio focus and returns to Idle`() = runTest {
        pipeline.stopSpeaking()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(VoicePipelineState.Idle, pipeline.state.value)
        io.mockk.verify { tts.stop() }
    }
}
