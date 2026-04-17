package com.opensmarthome.speaker.voice.tts

import android.content.Context
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import com.opensmarthome.speaker.data.preferences.SecurePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import timber.log.Timber

/**
 * Routes TTS calls to the configured provider (Android / OpenAI / ElevenLabs / VOICEVOX).
 *
 * Acts as a TextToSpeech implementation so VoicePipeline doesn't need to know about
 * the provider selection. Settings changes take effect on the next speak() call.
 *
 * On construction, persisted TTS_SPEECH_RATE, TTS_PITCH, and TTS_ENGINE are read from
 * [preferences] and applied to [androidTtsProvider] so that user-configured settings
 * survive app restarts.
 */
class TtsManager(
    private val context: Context,
    private val preferences: AppPreferences,
    private val securePreferences: SecurePreferences,
    private val httpClient: OkHttpClient,
    /**
     * Visible internally for testing. Production callers omit this parameter;
     * the default creates and initialises a real [AndroidTtsProvider].
     */
    internal val androidTtsProvider: AndroidTtsProvider =
        AndroidTtsProvider(context).also { it.initialize() }
) : TextToSpeech {

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    /**
     * Manager-level karaoke-style current-chunk flow. It mirrors whichever
     * provider is currently active, switching sources whenever the user
     * swaps TTS engines in Settings. See [bindChunkForwarder].
     */
    private val _currentChunk = MutableStateFlow("")
    override val currentChunk: StateFlow<String> = _currentChunk.asStateFlow()

    /**
     * Lazy scope used only for bridging provider chunk flows into the
     * manager-level [currentChunk]. Kept local so callers do not have to
     * manage a lifecycle — the collectors are cancelled on every provider
     * swap by [bindChunkForwarder].
     */
    // Unconfined so upstream emissions propagate synchronously — StateFlow is
    // conflating + thread-safe and the collector only copies a string, so
    // thread-hopping is unnecessary and would delay UI updates.
    private val chunkForwarderScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var chunkForwarderJob: Job? = null

    // Lazily create network-backed providers as needed
    private val openAiProvider: OpenAiTtsProvider by lazy {
        OpenAiTtsProvider(context, preferences, securePreferences, httpClient)
    }
    private val elevenLabsProvider: ElevenLabsTtsProvider by lazy {
        ElevenLabsTtsProvider(context, preferences, securePreferences, httpClient)
    }
    private val voiceVoxProvider: VoiceVoxTtsProvider by lazy {
        VoiceVoxTtsProvider(context, preferences, httpClient)
    }
    private val piperProvider: PiperTtsProvider by lazy {
        // Piper is a placeholder that falls back to Android system TTS until
        // the piper-cpp JNI bindings land. See PiperTtsProvider.
        PiperTtsProvider(androidTtsProvider)
    }

    @Volatile private var currentProvider: TextToSpeech = androidTtsProvider

    init {
        applyPersistedSettings()
        bindChunkForwarder(currentProvider)
    }

    /**
     * Cancels the previous per-chunk forwarder and launches a fresh one that
     * mirrors [provider]'s [TextToSpeech.currentChunk] into this manager's
     * [currentChunk]. Seeds synchronously with the provider's current value
     * so the UI can observe the active chunk without waiting for the
     * collector to start.
     */
    private fun bindChunkForwarder(provider: TextToSpeech) {
        chunkForwarderJob?.cancel()
        // Seed synchronously: tests (and first-frame UI) expect value without
        // waiting for the collecting coroutine to be scheduled.
        _currentChunk.value = provider.currentChunk.value
        chunkForwarderJob = chunkForwarderScope.launch {
            provider.currentChunk.collect { chunk ->
                _currentChunk.value = chunk
            }
        }
    }

    /**
     * Reads persisted TTS settings from [preferences] and applies them to
     * [androidTtsProvider] so that user-configured speech rate, pitch, and
     * engine survive app restarts.
     *
     * Uses [runBlocking] — the same pattern as [resolveProvider] — because this
     * runs during construction before any coroutine scope is available.
     */
    private fun applyPersistedSettings() {
        val rate = runBlocking { preferences.observe(PreferenceKeys.TTS_SPEECH_RATE).first() }
        val pitch = runBlocking { preferences.observe(PreferenceKeys.TTS_PITCH).first() }
        val engine = runBlocking { preferences.observe(PreferenceKeys.TTS_ENGINE).first() }

        if (rate != null) {
            Timber.d("TTS: restoring speech rate=$rate from preferences")
            androidTtsProvider.setSpeechRate(rate)
        }
        if (pitch != null) {
            Timber.d("TTS: restoring pitch=$pitch from preferences")
            androidTtsProvider.setPitch(pitch)
        }
        if (!engine.isNullOrBlank()) {
            Timber.d("TTS: restoring engine=$engine from preferences")
            androidTtsProvider.reinitialize(engine)
        }
    }

    private fun resolveProvider(): TextToSpeech {
        val id = runBlocking { preferences.observe(PreferenceKeys.TTS_PROVIDER).first() }
            ?.lowercase()?.trim() ?: "android"
        val next: TextToSpeech = when (id) {
            "openai" -> openAiProvider
            "elevenlabs" -> elevenLabsProvider
            "voicevox" -> voiceVoxProvider
            "piper" -> piperProvider
            else -> androidTtsProvider
        }
        if (next !== currentProvider) {
            Timber.d("TTS provider switched: $id")
            // Stop the previous provider before switching
            try { currentProvider.stop() } catch (_: Exception) {}
            currentProvider = next
            // Clear stale chunk from the previous provider immediately so the
            // UI does not linger on an already-spoken sentence during swap.
            _currentChunk.value = ""
            bindChunkForwarder(next)
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
        // Clear the rolling-chunk text so the UI falls back to the full
        // lastResponse while idle. Providers also clear their own chunk on
        // stop(); this guards the manager-level cache.
        _currentChunk.value = ""
    }

    /**
     * Expose AndroidTtsProvider configuration methods for Settings UI.
     * Other providers pull their config from preferences directly at speak time.
     */
    fun setSpeechRate(rate: Float) = androidTtsProvider.setSpeechRate(rate)
    fun setPitch(pitch: Float) = androidTtsProvider.setPitch(pitch)
    fun setLanguage(tag: String) = androidTtsProvider.setLanguage(tag)
    fun reinitialize(engine: String? = null) = androidTtsProvider.reinitialize(engine)
}
