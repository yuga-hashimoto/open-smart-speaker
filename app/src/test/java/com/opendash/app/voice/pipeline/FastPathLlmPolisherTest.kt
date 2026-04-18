package com.opendash.app.voice.pipeline

import com.google.common.truth.Truth.assertThat
import com.opendash.app.assistant.model.AssistantMessage
import com.opendash.app.assistant.model.AssistantSession
import com.opendash.app.assistant.provider.AssistantProvider
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

    // Pin the default locale to en-US so existing assertions that rely on
    // "null tag => English" remain deterministic. Locale-fallback tests
    // below spin up their own polisher with a custom supplier.
    private val polisher = FastPathLlmPolisher(
        timeoutMs = 2_000L,
        defaultLocaleTagSupplier = { "en-US" }
    )
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

    // --- Bug A: device-locale fallback when TTS_LANGUAGE pref is unset ---

    @Test
    fun `buildPrompt falls back to Japanese when tag is null and device locale is ja`() {
        val jaPolisher = FastPathLlmPolisher(
            timeoutMs = 2_000L,
            defaultLocaleTagSupplier = { "ja-JP" }
        )
        val prompt = jaPolisher.buildPrompt(
            userText = "宗像市の天気を教えて",
            resultData = """{"location":"Munakata","temperature_c":16.3}""",
            ttsLanguageTag = null
        )
        assertThat(prompt).contains("日本語で答えてください")
    }

    @Test
    fun `buildPrompt falls back to Japanese when tag is blank and device locale is ja`() {
        val jaPolisher = FastPathLlmPolisher(
            timeoutMs = 2_000L,
            defaultLocaleTagSupplier = { "ja-JP" }
        )
        val prompt = jaPolisher.buildPrompt(
            userText = "天気",
            resultData = """{"location":"Tokyo"}""",
            ttsLanguageTag = ""
        )
        assertThat(prompt).contains("日本語で答えてください")
    }

    @Test
    fun `buildPrompt honors explicit ja-JP tag regardless of device locale`() {
        val enPolisher = FastPathLlmPolisher(
            timeoutMs = 2_000L,
            defaultLocaleTagSupplier = { "en-US" }
        )
        val prompt = enPolisher.buildPrompt(
            userText = "天気",
            resultData = """{"location":"Tokyo"}""",
            ttsLanguageTag = "ja-JP"
        )
        assertThat(prompt).contains("日本語で答えてください")
    }

    @Test
    fun `buildPrompt stays English when tag is null and device locale is en`() {
        val enPolisher = FastPathLlmPolisher(
            timeoutMs = 2_000L,
            defaultLocaleTagSupplier = { "en-US" }
        )
        val prompt = enPolisher.buildPrompt(
            userText = "weather",
            resultData = """{"location":"Tokyo"}""",
            ttsLanguageTag = null
        )
        assertThat(prompt).contains("Respond in English.")
    }

    @Test
    fun `resolveLanguageTag prefers explicit tag when present`() {
        val p = FastPathLlmPolisher(defaultLocaleTagSupplier = { "ja-JP" })
        assertThat(p.resolveLanguageTag("fr-FR")).isEqualTo("fr-FR")
    }

    @Test
    fun `resolveLanguageTag falls back to locale supplier when tag is blank`() {
        val p = FastPathLlmPolisher(defaultLocaleTagSupplier = { "ja-JP" })
        assertThat(p.resolveLanguageTag(null)).isEqualTo("ja-JP")
        assertThat(p.resolveLanguageTag("")).isEqualTo("ja-JP")
        assertThat(p.resolveLanguageTag("   ")).isEqualTo("ja-JP")
    }

    // --- Bug B: pre-summary replaces raw JSON in the prompt ---

    @Test
    fun `buildPrompt for get_forecast contains pre-summary instead of raw JSON`() {
        val forecastJson =
            """[{"date":"2026-04-18","min_c":12.0,"max_c":18.0,"condition":"Partly cloudy"},""" +
                """{"date":"2026-04-19","min_c":10.0,"max_c":16.0,"condition":"Clear"}]"""
        val prompt = polisher.buildPrompt(
            userText = "天気予報",
            resultData = forecastJson,
            ttsLanguageTag = "ja-JP",
            toolName = "get_forecast"
        )
        // Pre-summary should be used — look for the day prefix.
        assertThat(prompt).contains("2026-04-18")
        assertThat(prompt).contains("Partly cloudy")
        // Raw JSON keys should NOT leak through.
        assertThat(prompt).doesNotContain("\"min_c\":")
        assertThat(prompt).doesNotContain("\"max_c\":")
    }

    @Test
    fun `buildPrompt for get_weather uses current summary prefix`() {
        val json =
            """{"location":"Munakata","temperature_c":16.3,"condition":"Clear","humidity":65}"""
        val prompt = polisher.buildPrompt(
            userText = "宗像市の天気",
            resultData = json,
            ttsLanguageTag = "ja-JP",
            toolName = "get_weather"
        )
        assertThat(prompt).contains("Current:")
        assertThat(prompt).contains("Munakata")
        // formatInt preserves the decimal on non-integer temps.
        assertThat(prompt).contains("16.3°C")
    }

    @Test
    fun `buildPrompt includes do not invent directive`() {
        val prompt = polisher.buildPrompt(
            userText = "weather",
            resultData = """{"location":"Tokyo"}""",
            ttsLanguageTag = "en-US",
            toolName = "get_weather"
        )
        assertThat(prompt).contains("do not invent")
    }

    @Test
    fun `buildPrompt for web_search with empty abstract mentions no results`() {
        val json = """{"query":"pixel 14","abstract":"","source_url":null,"related":[]}"""
        val prompt = polisher.buildPrompt(
            userText = "pixel 14",
            resultData = json,
            ttsLanguageTag = "en-US",
            toolName = "web_search"
        )
        assertThat(prompt).contains("No results")
    }
}
