package com.opendash.app.voice.tts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Placeholder for the Piper on-device neural TTS backend (VITS, via piper-cpp JNI).
 *
 * Today this delegates to the supplied [fallback] — typically the Android system
 * TTS — and logs a warning. Future work wires the real JNI bindings and voice
 * model download flow; when that lands, delete the fallback path.
 *
 * Keeping the class live now lets us ship the Settings UI and route via
 * TTS_PROVIDER = "piper" so users can opt in once the real backend arrives,
 * without needing another PR to the routing code.
 *
 * Ref: rhasspy/piper.
 */
class PiperTtsProvider(
    private val fallback: TextToSpeech
) : TextToSpeech {

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    override suspend fun speak(text: String) {
        Timber.w("Piper TTS not yet wired — falling back to Android system TTS")
        _isSpeaking.value = true
        try {
            fallback.speak(text)
        } finally {
            _isSpeaking.value = false
        }
    }

    override fun stop() {
        fallback.stop()
        _isSpeaking.value = false
    }
}
