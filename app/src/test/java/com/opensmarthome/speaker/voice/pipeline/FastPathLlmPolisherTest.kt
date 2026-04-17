package com.opensmarthome.speaker.voice.pipeline

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.model.AssistantSession
import com.opensmarthome.speaker.assistant.provider.AssistantProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FastPathLlmPolisherTest {

    private val polisher = FastPathLlmPolisher(timeoutMs = 2_000L)
    private val session = AssistantSession(providerId = "test")

    @Test
    fun `polish returns assistant reply for get_weather`() = runTest {
        val provider = mockk<AssistantProvider>(relaxed = true)
        coEvery { provider.startSession(any()) } returns session
        coEvery { provider.send(any(), any(), any()) } returns
            AssistantMessage.Assistant(content = "It's 18 degrees and clear in Tokyo.")

        val spoken = polisher.polish(
            provider = provider,
            toolName = "get_weather",
            userText = "What's the weather?",
            resultData = """{"location":"Tokyo","temperature_c":18,"condition":"Clear"}""",
            ttsLanguageTag = "en-US"
        )

        assertThat(spoken).isEqualTo("It's 18 degrees and clear in Tokyo.")
        coVerify { provider.send(any(), any(), any()) }
    }

    @Test
    fun `polish returns null for unsupported tool`() = runTest {
        val provider = mockk<AssistantProvider>(relaxed = true)

        val spoken = polisher.polish(
            provider = provider,
            toolName = "set_timer",
            userText = "Set a timer",
            resultData = """{"ok":true}""",
            ttsLanguageTag = "en-US"
        )

        assertThat(spoken).isNull()
        coVerify(exactly = 0) { provider.send(any(), any(), any()) }
    }

    @Test
    fun `polish returns null when tool data is blank`() = runTest {
        val provider = mockk<AssistantProvider>(relaxed = true)

        val spoken = polisher.polish(
            provider = provider,
            toolName = "get_weather",
            userText = "weather",
            resultData = "",
            ttsLanguageTag = "en-US"
        )

        assertThat(spoken).isNull()
        coVerify(exactly = 0) { provider.send(any(), any(), any()) }
    }

    @Test
    fun `polish returns null on provider timeout`() = runTest(StandardTestDispatcher()) {
        val slowPolisher = FastPathLlmPolisher(timeoutMs = 50L)
        val provider = mockk<AssistantProvider>(relaxed = true)
        coEvery { provider.startSession(any()) } returns session
        coEvery { provider.send(any(), any(), any()) } coAnswers {
            delay(5_000) // far beyond the 50ms timeout
            AssistantMessage.Assistant(content = "too late")
        }

        val spoken = slowPolisher.polish(
            provider = provider,
            toolName = "get_weather",
            userText = "weather",
            resultData = """{"location":"Tokyo"}""",
            ttsLanguageTag = "en-US"
        )

        assertThat(spoken).isNull()
    }

    @Test
    fun `polish returns null on provider exception`() = runTest {
        val provider = mockk<AssistantProvider>(relaxed = true)
        coEvery { provider.startSession(any()) } returns session
        coEvery { provider.send(any(), any(), any()) } throws RuntimeException("boom")

        val spoken = polisher.polish(
            provider = provider,
            toolName = "get_weather",
            userText = "weather",
            resultData = """{"location":"Tokyo"}""",
            ttsLanguageTag = "en-US"
        )

        assertThat(spoken).isNull()
    }

    @Test
    fun `polish returns null when reply is blank`() = runTest {
        val provider = mockk<AssistantProvider>(relaxed = true)
        coEvery { provider.startSession(any()) } returns session
        coEvery { provider.send(any(), any(), any()) } returns
            AssistantMessage.Assistant(content = "   ")

        val spoken = polisher.polish(
            provider = provider,
            toolName = "get_weather",
            userText = "weather",
            resultData = """{"location":"Tokyo"}""",
            ttsLanguageTag = "en-US"
        )

        assertThat(spoken).isNull()
    }

    @Test
    fun `buildPrompt includes Japanese directive for ja locale`() {
        val prompt = polisher.buildPrompt(
            userText = "天気は?",
            resultData = """{"location":"東京"}""",
            ttsLanguageTag = "ja-JP"
        )
        assertThat(prompt).contains("日本語で答えてください")
        assertThat(prompt).contains("天気は?")
        assertThat(prompt).contains("東京")
    }

    @Test
    fun `buildPrompt includes English directive for en locale`() {
        val prompt = polisher.buildPrompt(
            userText = "weather",
            resultData = """{"location":"Tokyo"}""",
            ttsLanguageTag = "en-US"
        )
        assertThat(prompt).contains("Respond in English.")
    }

    @Test
    fun `buildPrompt defaults to English for null locale`() {
        val prompt = polisher.buildPrompt(
            userText = "weather",
            resultData = """{"location":"Tokyo"}""",
            ttsLanguageTag = null
        )
        assertThat(prompt).contains("Respond in English.")
    }

    @Test
    fun `default timeout is 20 seconds for slow on-device LLM`() {
        // Gemma 4 E2B takes 5-10s to polish on real devices; 4s was causing
        // near-100% fallback. Default must be well above p99 generation time.
        assertThat(FastPathLlmPolisher.DEFAULT_TIMEOUT_MS).isEqualTo(20_000L)
    }

    @Test
    fun `buildPrompt handles other supported locales`() {
        val es = polisher.buildPrompt("hola", "{}", "es-ES")
        assertThat(es).contains("español")
        val fr = polisher.buildPrompt("bonjour", "{}", "fr-FR")
        assertThat(fr).contains("français")
        val de = polisher.buildPrompt("hallo", "{}", "de-DE")
        assertThat(de).contains("Deutsch")
        val ko = polisher.buildPrompt("hi", "{}", "ko-KR")
        assertThat(ko).contains("한국어")
        val zh = polisher.buildPrompt("你好", "{}", "zh-CN")
        assertThat(zh).contains("中文")
    }
}
