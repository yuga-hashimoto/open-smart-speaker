package com.opensmarthome.speaker.voice.tts

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Verifies that [TtsManager.currentChunk] reflects the active provider's
 * own [TextToSpeech.currentChunk], enabling the UI to show a karaoke-style
 * rolling display of what the TTS engine is currently speaking.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TtsManagerCurrentChunkTest {

    private class ChunkyProvider : TextToSpeech {
        val _chunk = MutableStateFlow("")
        val _speaking = MutableStateFlow(false)
        override val isSpeaking: StateFlow<Boolean> = _speaking.asStateFlow()
        override val currentChunk: StateFlow<String> = _chunk.asStateFlow()
        override suspend fun speak(text: String) = Unit
        override fun stop() { _chunk.value = "" }
    }

    private class FakeAndroidTtsProvider(
        private val chunkSource: MutableStateFlow<String>
    ) : AndroidTtsProvider(mockk(relaxed = true)) {
        override fun initialize(engine: String?) = Unit
        override val currentChunk: StateFlow<String> = chunkSource.asStateFlow()
    }

    private fun buildPrefs(providerId: String? = null): AppPreferences =
        mockk<AppPreferences>(relaxed = true).also { prefs ->
            every { prefs.observe(PreferenceKeys.TTS_SPEECH_RATE) } returns MutableStateFlow(null)
            every { prefs.observe(PreferenceKeys.TTS_PITCH) } returns MutableStateFlow(null)
            every { prefs.observe(PreferenceKeys.TTS_ENGINE) } returns MutableStateFlow(null)
            every { prefs.observe(PreferenceKeys.TTS_PROVIDER) } returns MutableStateFlow(providerId)
        }

    @Test
    fun `currentChunk mirrors android provider chunk updates`() = runTest {
        val source = MutableStateFlow("")
        val fake = FakeAndroidTtsProvider(source)
        val manager = TtsManager(
            context = mockk(relaxed = true),
            preferences = buildPrefs(providerId = "android"),
            securePreferences = mockk(relaxed = true),
            httpClient = mockk(relaxed = true),
            androidTtsProvider = fake
        )

        assertThat(manager.currentChunk.value).isEmpty()
        source.value = "First sentence."
        assertThat(manager.currentChunk.value).isEqualTo("First sentence.")
        source.value = "Second sentence."
        assertThat(manager.currentChunk.value).isEqualTo("Second sentence.")
        source.value = ""
        assertThat(manager.currentChunk.value).isEmpty()
    }

    @Test
    fun `stop clears currentChunk`() = runTest {
        val source = MutableStateFlow("Active chunk")
        val fake = FakeAndroidTtsProvider(source)
        val manager = TtsManager(
            context = mockk(relaxed = true),
            preferences = buildPrefs(providerId = "android"),
            securePreferences = mockk(relaxed = true),
            httpClient = mockk(relaxed = true),
            androidTtsProvider = fake
        )

        assertThat(manager.currentChunk.value).isEqualTo("Active chunk")
        // stop should clear the manager-level chunk so UI resets
        manager.stop()
        assertThat(manager.currentChunk.value).isEmpty()
    }
}
