package com.opensmarthome.speaker.voice.tts

import android.content.Context
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import com.opensmarthome.speaker.data.preferences.SecurePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Routes TTS calls to the configured provider (Android / OpenAI / ElevenLabs / VOICEVOX).
 *
 * Acts as a TextToSpeech implementation so VoicePipeline doesn't need to know about
 * the provider selection. Settings changes take effect on the next speak() call.
 */
class TtsManager(
    private val context: Context,
    private val preferences: AppPreferences,
    private val securePreferences: SecurePreferences
) : TextToSpeech {

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Lazily create providers as needed
    private val androidProvider: AndroidTtsProvider by lazy {
        AndroidTtsProvider(context).also { it.initialize() }
    }
    private val openAiProvider: OpenAiTtsProvider by lazy {
        OpenAiTtsProvider(context, preferences, securePreferences, httpClient)
    }
    private val elevenLabsProvider: ElevenLabsTtsProvider by lazy {
        ElevenLabsTtsProvider(context, preferences, securePreferences, httpClient)
    }
    private val voiceVoxProvider: VoiceVoxTtsProvider by lazy {
        VoiceVoxTtsProvider(context, preferences, httpClient)
    }

    @Volatile private var currentProvider: TextToSpeech = androidProvider

    private fun resolveProvider(): TextToSpeech {
        val id = runBlocking { preferences.observe(PreferenceKeys.TTS_PROVIDER).first() }
            ?.lowercase()?.trim() ?: "android"
        val next: TextToSpeech = when (id) {
            "openai" -> openAiProvider
            "elevenlabs" -> elevenLabsProvider
            "voicevox" -> voiceVoxProvider
            else -> androidProvider
        }
        if (next !== currentProvider) {
            Timber.d("TTS provider switched: $id")
            // Stop the previous provider before switching
            try { currentProvider.stop() } catch (_: Exception) {}
            currentProvider = next
        }
        return next
    }

    override suspend fun speak(text: String) {
        val provider = resolveProvider()
        _isSpeaking.value = true
        try {
            provider.speak(text)
        } finally {
            _isSpeaking.value = false
        }
    }

    override fun stop() {
        try {
            currentProvider.stop()
        } catch (_: Exception) {}
        _isSpeaking.value = false
    }

    /**
     * Expose AndroidTtsProvider configuration methods for Settings UI.
     * Other providers pull their config from preferences directly at speak time.
     */
    fun setSpeechRate(rate: Float) = androidProvider.setSpeechRate(rate)
    fun setPitch(pitch: Float) = androidProvider.setPitch(pitch)
    fun setLanguage(tag: String) = androidProvider.setLanguage(tag)
    fun reinitialize(engine: String? = null) = androidProvider.reinitialize(engine)
}
