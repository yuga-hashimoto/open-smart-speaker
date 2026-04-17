package com.opensmarthome.speaker.voice.pipeline

import android.content.Context
import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.model.AssistantSession
import com.opensmarthome.speaker.assistant.provider.AssistantProvider
import com.opensmarthome.speaker.assistant.router.ConversationRouter
import com.opensmarthome.speaker.assistant.router.RoutingPolicy
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import com.opensmarthome.speaker.service.VoiceService
import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.voice.fastpath.FastPathMatch
import com.opensmarthome.speaker.voice.fastpath.FastPathRouter
import com.opensmarthome.speaker.voice.stt.SpeechToText
import com.opensmarthome.speaker.voice.stt.SttResult
import com.opensmarthome.speaker.voice.tts.TextToSpeech
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Covers continuous-conversation behaviour on both LLM and fast-path
 * turn endings. The previous implementation only re-armed listening after
 * LLM replies — fast-path commands (weather, web search, timer) ended
 * the turn in Idle, silently breaking the "Continuous Conversation"
 * setting that users had toggled on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoicePipelineContinuousModeTest {

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

        every { preferences.observe<Boolean>(any()) } returns flowOf(null)
        every { preferences.observe<Long>(any()) } returns flowOf(null)
        every { preferences.observe<String>(any()) } returns flowOf(null)

        coEvery { provider.startSession(any()) } returns AssistantSession(providerId = "test")
        coEvery { toolExecutor.availableTools() } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildPipeline(fastPathRouter: FastPathRouter? = null) {
        pipeline = VoicePipeline(
            context = context,
            stt = stt,
            tts = tts,
            router = router,
            toolExecutor = toolExecutor,
            moshi = moshi,
            preferences = preferences,
            fastPathRouter = fastPathRouter
        )
    }

    @Test
    fun `fast-path success with continuous mode restarts listening`() = runTest {
        mockkObject(VoiceService.Companion)
        try {
            every { VoiceService.resumeHotword(any()) } just Runs
            every { VoiceService.pauseHotword(any()) } just Runs

            every { preferences.observe(PreferenceKeys.CONTINUOUS_MODE) } returns flowOf(true)
            every { preferences.observe(PreferenceKeys.TTS_ENABLED) } returns flowOf(true)
            every { preferences.observe(PreferenceKeys.BARGE_IN_ENABLED) } returns flowOf(true)

            val fastPathRouter = mockk<FastPathRouter>()
            // Match only on the initial invocation; after continuous mode
            // triggers the restart, further matches return null so the
            // pipeline falls through to STT (which emits nothing) and lands
            // back in Idle — keeping the test deterministic instead of
            // looping forever.
            var matches = 0
            every { fastPathRouter.match(any()) } answers {
                matches++
                if (matches == 1) {
                    FastPathMatch(
                        toolName = null,
                        arguments = emptyMap(),
                        spokenConfirmation = "Timer set."
                    )
                } else {
                    null
                }
            }
            every { stt.startListening() } returns emptyFlow()

            buildPipeline(fastPathRouter)

            pipeline.processUserInput("set a timer")
            testDispatcher.scheduler.advanceTimeBy(10_000)
            testDispatcher.scheduler.advanceUntilIdle()

            // Fast-path spoke the confirmation and then tried to restart
            // listening because continuous mode was enabled. stt.startListening
            // was invoked as the re-arm signal.
            coVerify { tts.speak("Timer set.") }
            io.mockk.verify(atLeast = 1) { stt.startListening() }
        } finally {
            unmockkObject(VoiceService.Companion)
        }
    }

    @Test
    fun `fast-path success without continuous mode returns to Idle`() = runTest {
        mockkObject(VoiceService.Companion)
        try {
            every { VoiceService.resumeHotword(any()) } just Runs
            every { VoiceService.pauseHotword(any()) } just Runs

            every { preferences.observe(PreferenceKeys.CONTINUOUS_MODE) } returns flowOf(false)
            every { preferences.observe(PreferenceKeys.TTS_ENABLED) } returns flowOf(true)
            every { preferences.observe(PreferenceKeys.BARGE_IN_ENABLED) } returns flowOf(false)

            val fastPathRouter = mockk<FastPathRouter>()
            every { fastPathRouter.match(any()) } returns FastPathMatch(
                toolName = null,
                arguments = emptyMap(),
                spokenConfirmation = "Done."
            )

            buildPipeline(fastPathRouter)

            pipeline.processUserInput("help")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(VoicePipelineState.Idle, pipeline.state.value)
            io.mockk.verify(exactly = 0) { stt.startListening() }
        } finally {
            unmockkObject(VoiceService.Companion)
        }
    }

    @Test
    fun `LLM success with continuous mode restarts listening`() = runTest {
        mockkObject(VoiceService.Companion)
        try {
            every { VoiceService.resumeHotword(any()) } just Runs
            every { VoiceService.pauseHotword(any()) } just Runs

            every { preferences.observe(PreferenceKeys.CONTINUOUS_MODE) } returns flowOf(true)
            every { preferences.observe(PreferenceKeys.TTS_ENABLED) } returns flowOf(true)
            every { preferences.observe(PreferenceKeys.BARGE_IN_ENABLED) } returns flowOf(true)

            coEvery { router.resolveProvider(any()) } returns provider
            coEvery { provider.send(any(), any(), any()) } returns
                AssistantMessage.Assistant(content = "Reply.")
            every { stt.startListening() } returns emptyFlow()

            buildPipeline()

            pipeline.processUserInput("Hi")
            testDispatcher.scheduler.advanceTimeBy(10_000)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { tts.speak("Reply.") }
            io.mockk.verify(atLeast = 1) { stt.startListening() }
        } finally {
            unmockkObject(VoiceService.Companion)
        }
    }

    @Test
    fun `error path ignores continuous mode and returns to Idle`() = runTest {
        mockkObject(VoiceService.Companion)
        try {
            every { VoiceService.resumeHotword(any()) } just Runs
            every { VoiceService.pauseHotword(any()) } just Runs

            // Continuous mode is ON, but an LLM error must still land in Idle
            // — continuous mode should never cause a restart loop on failure.
            every { preferences.observe(PreferenceKeys.CONTINUOUS_MODE) } returns flowOf(true)
            every { preferences.observe(PreferenceKeys.TTS_ENABLED) } returns flowOf(true)
            every { preferences.observe(PreferenceKeys.BARGE_IN_ENABLED) } returns flowOf(true)

            coEvery { router.resolveProvider(any()) } throws RuntimeException("No providers")

            buildPipeline()

            pipeline.processUserInput("anything")
            testDispatcher.scheduler.advanceTimeBy(6000)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(VoicePipelineState.Idle, pipeline.state.value)
            io.mockk.verify(exactly = 0) { stt.startListening() }
        } finally {
            unmockkObject(VoiceService.Companion)
        }
    }
}
