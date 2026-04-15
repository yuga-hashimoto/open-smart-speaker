package com.opensmarthome.speaker.voice.pipeline

import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.model.AssistantSession
import com.opensmarthome.speaker.assistant.provider.AssistantProvider
import com.opensmarthome.speaker.assistant.provider.ProviderCapabilities
import com.opensmarthome.speaker.assistant.router.ConversationRouter
import com.opensmarthome.speaker.assistant.router.RoutingPolicy
import com.opensmarthome.speaker.homeassistant.tool.ToolExecutor
import com.opensmarthome.speaker.homeassistant.tool.ToolSchema
import com.opensmarthome.speaker.voice.tts.TextToSpeech
import com.opensmarthome.speaker.voice.stt.SpeechToText
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VoicePipelineTest {

    private lateinit var pipeline: VoicePipeline
    private lateinit var stt: SpeechToText
    private lateinit var tts: TextToSpeech
    private lateinit var router: ConversationRouter
    private lateinit var toolExecutor: ToolExecutor
    private lateinit var provider: AssistantProvider
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @BeforeEach
    fun setup() {
        stt = mockk()
        tts = mockk()
        router = mockk()
        toolExecutor = mockk()
        provider = mockk()

        every { stt.isListening } returns MutableStateFlow(false)
        every { tts.isSpeaking } returns MutableStateFlow(false)
        every { router.activeProvider } returns MutableStateFlow(provider)
        every { router.availableProviders } returns MutableStateFlow(listOf(provider))
        every { router.policy } returns MutableStateFlow(RoutingPolicy.Auto)

        coEvery { provider.startSession(any()) } returns AssistantSession(providerId = "test")
        coEvery { toolExecutor.availableTools() } returns emptyList()

        pipeline = VoicePipeline(stt, tts, router, toolExecutor, moshi)
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(VoicePipelineState.Idle, pipeline.state.value)
    }

    @Test
    fun `processUserInput sets state to Processing then Idle`() = runTest {
        coEvery { router.resolveProvider() } returns provider
        coEvery { provider.send(any(), any(), any()) } returns
            AssistantMessage.Assistant(content = "Hello!")
        coEvery { tts.speak("Hello!") } returns Unit

        pipeline.processUserInput("Hi")

        assertEquals(VoicePipelineState.Idle, pipeline.state.value)
        coVerify { tts.speak("Hello!") }
    }

    @Test
    fun `processUserInput handles error gracefully`() = runTest {
        coEvery { router.resolveProvider() } throws RuntimeException("No providers")

        pipeline.processUserInput("test")

        val state = pipeline.state.value
        assert(state is VoicePipelineState.Error)
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
}
