package com.opendash.app.e2e.fakes

import com.opendash.app.voice.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Test double for [TextToSpeech] that records every spoken utterance
 * instead of driving the device speakers.
 *
 * Stays a real [TextToSpeech] singleton so the rest of the voice graph
 * (VoicePipeline, FillerPhrases, fast-path) sees an identical contract.
 *
 * Thread-safe: `speak()` may be called from `tts.speak(...)` calls on
 * the pipeline's coroutine context, while assertions are made on the
 * test thread. [spokenTexts] uses CopyOnWriteArrayList for safe iteration.
 */
class FakeTextToSpeech : TextToSpeech {

    private val _spokenTexts = CopyOnWriteArrayList<String>()
    val spokenTexts: List<String> get() = _spokenTexts.toList()

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentChunk = MutableStateFlow("")
    override val currentChunk: StateFlow<String> = _currentChunk.asStateFlow()

    override suspend fun speak(text: String) {
        _isSpeaking.value = true
        _currentChunk.value = text
        _spokenTexts.add(text)
        _isSpeaking.value = false
    }

    override fun stop() {
        _isSpeaking.value = false
        _currentChunk.value = ""
    }

    fun reset() {
        _spokenTexts.clear()
        _currentChunk.value = ""
        _isSpeaking.value = false
    }
}
