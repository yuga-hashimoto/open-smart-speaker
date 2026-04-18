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

/**
 * Bug B regression: a fast-path turn (e.g. `web_search`) followed by an
 * LLM-only follow-up (e.g. "なぜ?") must pass the first turn's user input
 * *and* assistant reply into the LLM provider on the second turn, so the
 * model can actually answer a follow-up instead of starting from scratch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoicePipelineConversationContextTest {

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
    fun `follow-up LLM turn receives fast-path user and assistant messages in history`() = runTest {
        // --- Turn 1: fast-path web_search ---
        every { fastPathRouter.match("LINE レンジャー を Web で検索して") } returns FastPathMatch(
            toolName = "web_search",
            arguments = mapOf("query" to "LINEレンジャー"),
            spokenConfirmation = null
        )
        // Turn 2: no fast-path match → falls through to LLM.
        every { fastPathRouter.match("なぜですか") } returns null

        coEvery { toolExecutor.execute(any<ToolCall>()) } returns ToolResult(
            callId = "fast_1",
            success = true,
            data = """{"query":"LINEレンジャー","results":[""" +
                """{"title":"LINEレンジャー 公式","url":"https://example.com","snippet":"パズルRPG。"}""" +
                """]}""",
            error = null
        )

        // Capture the exact message list passed to provider.send on turn 2.
        val messagesSlot = slot<List<AssistantMessage>>()
        coEvery {
            provider.send(any(), capture(messagesSlot), any())
        } returns AssistantMessage.Assistant(content = "前のターンの検索結果についてですね。")

        coEvery { tts.speak(any()) } returns Unit

        val pipeline = buildPipeline()

        // Turn 1 — fast path handles end-to-end, adds user + assistant to history.
        pipeline.processUserInput("LINE レンジャー を Web で検索して")
        testDispatcher.scheduler.advanceUntilIdle()

        // Turn 2 — LLM-only follow-up.
        pipeline.processUserInput("なぜですか")
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(messagesSlot.isCaptured).isTrue()
        val messages = messagesSlot.captured
        // History must contain: [turn1 user, turn1 assistant, turn2 user]
        val userContents = messages.filterIsInstance<AssistantMessage.User>().map { it.content }
        val assistantContents =
            messages.filterIsInstance<AssistantMessage.Assistant>().map { it.content }

        assertThat(userContents).contains("LINE レンジャー を Web で検索して")
        assertThat(userContents).contains("なぜですか")
        // The fast-path assistant reply is persisted and visible to the LLM on turn 2.
        val turn1AssistantPresent = assistantContents.any {
            it.contains("LINEレンジャー") || it.contains("公式") || it.contains("パズルRPG")
        }
        assertThat(turn1AssistantPresent).isTrue()
    }
}
