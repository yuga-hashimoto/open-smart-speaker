package com.opendash.app.voice.pipeline

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.opendash.app.assistant.model.AssistantMessage
import com.opendash.app.assistant.model.AssistantSession
import com.opendash.app.assistant.provider.AssistantProvider
import com.opendash.app.assistant.router.ConversationRouter
import com.opendash.app.assistant.router.RoutingPolicy
import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.data.preferences.PreferenceKeys
import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolResult
import com.opendash.app.voice.fastpath.FastPathMatch
import com.opendash.app.voice.fastpath.FastPathRouter
import com.opendash.app.voice.stt.SpeechToText
import com.opendash.app.voice.tts.TextToSpeech
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoicePipelineFastPathInfoToolTest {

    private lateinit var context: Context
    private lateinit var stt: SpeechToText
    private lateinit var tts: TextToSpeech
    private lateinit var router: ConversationRouter
    private lateinit var toolExecutor: ToolExecutor
    private lateinit var provider: AssistantProvider
    private lateinit var preferences: AppPreferences
    private lateinit var fastPathRouter: FastPathRouter
    private lateinit var polisher: FastPathLlmPolisher
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
        fastPathRouter = mockk(relaxed = true)
        polisher = mockk(relaxed = true)

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
        every { preferences.observe(PreferenceKeys.TTS_ENABLED) } returns flowOf(true)

        coEvery { provider.startSession(any()) } returns AssistantSession(providerId = "test")
        coEvery { router.resolveProvider(any()) } returns provider
        coEvery { toolExecutor.availableTools() } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildPipeline(): VoicePipeline = VoicePipeline(
        context = context,
        stt = stt,
        tts = tts,
        router = router,
        toolExecutor = toolExecutor,
        moshi = moshi,
        preferences = preferences,
        fastPathRouter = fastPathRouter,
        fastPathLlmPolisher = polisher
    )

    @Test
    fun `get_weather fast-path uses LLM polisher when available`() = runTest {
        every { fastPathRouter.match("what's the weather") } returns FastPathMatch(
            toolName = "get_weather",
            arguments = emptyMap(),
            spokenConfirmation = null
        )
        coEvery { toolExecutor.execute(any<ToolCall>()) } returns ToolResult(
            callId = "fast_1",
            success = true,
            data = """{"location":"Tokyo","temperature_c":18,"condition":"Clear"}""",
            error = null
        )
        coEvery {
            polisher.polish(
                provider = any(),
                toolName = "get_weather",
                userText = "what's the weather",
                resultData = any(),
                ttsLanguageTag = any()
            )
        } returns "It's 18 degrees and clear in Tokyo right now."

        val pipeline = buildPipeline()
        pipeline.processUserInput("what's the weather")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { polisher.polish(any(), "get_weather", "what's the weather", any(), any()) }
        coVerify { tts.speak("It's 18 degrees and clear in Tokyo right now.") }
        assertThat(pipeline.lastResponse.value)
            .isEqualTo("It's 18 degrees and clear in Tokyo right now.")
    }

    @Test
    fun `get_weather falls back to formatter when LLM polisher returns null`() = runTest {
        every { fastPathRouter.match(any()) } returns FastPathMatch(
            toolName = "get_weather",
            arguments = emptyMap(),
            spokenConfirmation = null
        )
        coEvery { toolExecutor.execute(any<ToolCall>()) } returns ToolResult(
            callId = "fast_1",
            success = true,
            data = """{"location":"Tokyo","temperature_c":18,"condition":"Clear"}""",
            error = null
        )
        coEvery { polisher.polish(any(), any(), any(), any(), any()) } returns null

        val spoken = slot<String>()
        coEvery { tts.speak(capture(spoken)) } returns Unit

        val pipeline = buildPipeline()
        pipeline.processUserInput("weather in tokyo")
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify fallback formatter was used — output should contain "Tokyo" + "18"
        assertThat(spoken.captured).contains("Tokyo")
        assertThat(spoken.captured).contains("18")
        coVerify { polisher.polish(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `set_timer fast-path does NOT call LLM polisher`() = runTest {
        every { fastPathRouter.match(any()) } returns FastPathMatch(
            toolName = "set_timer",
            arguments = mapOf("duration" to 300),
            spokenConfirmation = "Timer set for 5 minutes."
        )
        coEvery { toolExecutor.execute(any<ToolCall>()) } returns ToolResult(
            callId = "fast_1",
            success = true,
            data = """{"ok":true}""",
            error = null
        )

        val pipeline = buildPipeline()
        pipeline.processUserInput("set a timer for 5 minutes")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { polisher.polish(any(), any(), any(), any(), any()) }
        coVerify { tts.speak("Timer set for 5 minutes.") }
    }

    @Test
    fun `get_news fast-path uses LLM polisher`() = runTest {
        every { fastPathRouter.match(any()) } returns FastPathMatch(
            toolName = "get_news",
            arguments = emptyMap(),
            spokenConfirmation = null
        )
        coEvery { toolExecutor.execute(any<ToolCall>()) } returns ToolResult(
            callId = "fast_1",
            success = true,
            data = """[{"title":"Big story","summary":"s","link":"l","published":"p"}]""",
            error = null
        )
        coEvery { polisher.polish(any(), "get_news", any(), any(), any()) } returns
            "Today's top story is Big story."

        val pipeline = buildPipeline()
        pipeline.processUserInput("news")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { tts.speak("Today's top story is Big story.") }
    }

    @Test
    fun `web_search fast-path polishes when LLM available, falls back to regex on null`() = runTest {
        // Historically web_search bypassed the polisher (Gemma 2B refused on
        // SERP snippets). After Gemma 4 E2B landed, VoicePipeline.kt now
        // polishes web_search too — see the "Polish every info tool" comment
        // around tryHandleFastPath. This test pins the current contract: the
        // polisher IS invoked, and when it returns null (failure / refusal),
        // the regex formatter's top-result sentence is spoken as a fallback.
        every { fastPathRouter.match(any()) } returns FastPathMatch(
            toolName = "web_search",
            arguments = mapOf("query" to "LINEレンジャー"),
            spokenConfirmation = null
        )
        coEvery { toolExecutor.execute(any<ToolCall>()) } returns ToolResult(
            callId = "fast_1",
            success = true,
            data = """{"query":"LINEレンジャー","results":[""" +
                """{"title":"LINEレンジャー 公式サイト","url":"https://example.com/1","snippet":"LINEレンジャーは人気のパズルRPGです。"}""" +
                """]}""",
            error = null
        )
        // Simulate polisher refusing / timing out so the regex fallback runs.
        coEvery { polisher.polish(any(), eq("web_search"), any(), any(), any()) } returns null

        val spoken = slot<String>()
        coEvery { tts.speak(capture(spoken)) } returns Unit

        val pipeline = buildPipeline()
        pipeline.processUserInput("LINE レンジャー を Web で検索して")
        testDispatcher.scheduler.advanceUntilIdle()

        // Polisher IS invoked for web_search under the new policy.
        coVerify { polisher.polish(any(), eq("web_search"), any(), any(), any()) }
        // When polish returns null we fall back to the regex formatter's
        // top-result sentence — top title + snippet should be audible.
        assertThat(spoken.captured).contains("LINEレンジャー 公式サイト")
        assertThat(spoken.captured).contains("人気のパズルRPG")
    }

    @Test
    fun `polisher is passed ja-JP language tag when TTS_LANGUAGE is ja`() = runTest {
        every { preferences.observe(PreferenceKeys.TTS_LANGUAGE) } returns flowOf("ja-JP")
        every { fastPathRouter.match(any()) } returns FastPathMatch(
            toolName = "get_weather",
            arguments = emptyMap(),
            spokenConfirmation = null
        )
        coEvery { toolExecutor.execute(any<ToolCall>()) } returns ToolResult(
            callId = "fast_1",
            success = true,
            data = """{"location":"東京"}""",
            error = null
        )
        coEvery { polisher.polish(any(), any(), any(), any(), any()) } returns
            "東京は18度、晴れです。"

        val pipeline = buildPipeline()
        pipeline.processUserInput("天気は?")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            polisher.polish(
                provider = any(),
                toolName = "get_weather",
                userText = "天気は?",
                resultData = any(),
                ttsLanguageTag = "ja-JP"
            )
        }
    }

    @Test
    fun `polisher is passed en-US language tag when TTS_LANGUAGE is en`() = runTest {
        every { preferences.observe(PreferenceKeys.TTS_LANGUAGE) } returns flowOf("en-US")
        every { fastPathRouter.match(any()) } returns FastPathMatch(
            toolName = "get_weather",
            arguments = emptyMap(),
            spokenConfirmation = null
        )
        coEvery { toolExecutor.execute(any<ToolCall>()) } returns ToolResult(
            callId = "fast_1",
            success = true,
            data = """{"location":"New York"}""",
            error = null
        )
        coEvery { polisher.polish(any(), any(), any(), any(), any()) } returns
            "It's sunny in New York."

        val pipeline = buildPipeline()
        pipeline.processUserInput("weather")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            polisher.polish(
                provider = any(),
                toolName = "get_weather",
                userText = "weather",
                resultData = any(),
                ttsLanguageTag = "en-US"
            )
        }
    }

    @Test
    fun `polisher failure does not break fast-path flow`() = runTest {
        every { fastPathRouter.match(any()) } returns FastPathMatch(
            toolName = "get_weather",
            arguments = emptyMap(),
            spokenConfirmation = null
        )
        coEvery { toolExecutor.execute(any<ToolCall>()) } returns ToolResult(
            callId = "fast_1",
            success = true,
            data = """{"location":"Tokyo","temperature_c":18,"condition":"Clear"}""",
            error = null
        )
        coEvery { polisher.polish(any(), any(), any(), any(), any()) } throws
            RuntimeException("polisher boom")

        val spoken = slot<String>()
        coEvery { tts.speak(capture(spoken)) } returns Unit

        val pipeline = buildPipeline()
        pipeline.processUserInput("weather")
        testDispatcher.scheduler.advanceUntilIdle()

        // Pipeline should land on Idle (not Error) and speak the fallback
        assertThat(pipeline.state.value).isEqualTo(VoicePipelineState.Idle)
        assertThat(spoken.captured).contains("Tokyo")
    }
}
