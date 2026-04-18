package com.opendash.app.voice.tts

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private class RecordingTts : TextToSpeech {
    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()
    var spokenCount = 0
    var lastText: String? = null
    var stopCount = 0

    override suspend fun speak(text: String) {
        spokenCount++
        lastText = text
        _isSpeaking.value = true
        _isSpeaking.value = false
    }

    override fun stop() { stopCount++ }
}

class PiperTtsProviderTest {

    @Test
    fun `speak delegates to fallback and forwards text verbatim`() = runTest {
        val fallback = RecordingTts()
        val piper = PiperTtsProvider(fallback)

        piper.speak("hello world")

        assertThat(fallback.spokenCount).isEqualTo(1)
        assertThat(fallback.lastText).isEqualTo("hello world")
        assertThat(piper.isSpeaking.value).isFalse()
    }

    @Test
    fun `stop delegates to fallback and clears isSpeaking`() {
        val fallback = RecordingTts()
        val piper = PiperTtsProvider(fallback)

        piper.stop()

        assertThat(fallback.stopCount).isEqualTo(1)
        assertThat(piper.isSpeaking.value).isFalse()
    }
}
