package com.opendash.app.voice.tts

import android.content.Context
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import android.speech.tts.TextToSpeech as AndroidTts

open class AndroidTtsProvider(context: Context) : TextToSpeech {

    private val appContext = context.applicationContext
    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentChunk = MutableStateFlow("")
    override val currentChunk: StateFlow<String> = _currentChunk.asStateFlow()

    private var tts: AndroidTts? = null
    @Volatile
    private var isInitialized = false
    private var pendingSpeak: (() -> Unit)? = null

    // Configurable settings
    private var speechRate: Float = 1.0f
    private var pitch: Float = 1.0f
    private var preferredEngine: String? = null
    private var languageTag: String? = null

    open fun initialize(engine: String? = null) {
        preferredEngine = engine
        Timber.d("TTS: initializing with engine=${engine ?: "default"}")

        val listener = AndroidTts.OnInitListener { status ->
            if (status == AndroidTts.SUCCESS) {
                onInitSuccess()
            } else {
                Timber.e("TTS init failed: $status")
            }
        }

        tts = if (engine != null) {
            AndroidTts(appContext, listener, engine)
        } else {
            AndroidTts(appContext, listener)
        }
    }

    private fun onInitSuccess() {
        isInitialized = true
        setupVoice()
        Timber.d("TTS initialized successfully")
        pendingSpeak?.invoke()
        pendingSpeak = null
    }

    private fun setupVoice() {
        val t = tts ?: return

        val locale = if (!languageTag.isNullOrEmpty()) {
            Locale.forLanguageTag(languageTag!!)
        } else {
            Locale.getDefault()
        }

        val result = t.setLanguage(locale)
        if (result == AndroidTts.LANG_MISSING_DATA || result == AndroidTts.LANG_NOT_SUPPORTED) {
            t.setLanguage(Locale.US)
        }

        t.setSpeechRate(speechRate)
        t.setPitch(pitch)

        // Select best offline voice for the language
        try {
            val targetLang = t.language?.language
            val bestVoice = t.voices
                ?.filter { it.locale.language == targetLang }
                ?.firstOrNull { !it.isNetworkConnectionRequired }
                ?: t.voices?.firstOrNull { it.locale.language == targetLang }
            bestVoice?.let {
                t.voice = it
                Timber.d("Selected voice: ${it.name} (${it.locale})")
            }
        } catch (e: Exception) {
            Timber.w("Error selecting voice: ${e.message}")
        }
    }

    open fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.25f, 4.0f)
        tts?.setSpeechRate(speechRate)
    }

    open fun setPitch(value: Float) {
        pitch = value.coerceIn(0.25f, 2.0f)
        tts?.setPitch(pitch)
    }

    fun setLanguage(tag: String) {
        languageTag = tag
        if (isInitialized) setupVoice()
    }

    open fun reinitialize(engine: String? = null) {
        tts?.shutdown()
        isInitialized = false
        initialize(engine)
    }

    override suspend fun speak(text: String) {
        if (text.isBlank()) return

        val cleanText = TtsUtils.stripMarkdownForSpeech(text)
        if (cleanText.isBlank()) return

        // Use sentence-level chunks so the karaoke display flips at every
        // sentence boundary rather than once per 3600-char super-chunk.
        // The engine's ~3900-char hard limit is still respected because our
        // sentenceHardCap (500) is far below it.
        val chunks = TtsUtils.splitIntoKaraokeChunks(cleanText)
        Timber.d(
            "TTS: ${chunks.size} chunk(s), totalChars=${cleanText.length}, mode=karaoke"
        )

        try {
            for ((i, chunk) in chunks.withIndex()) {
                val queueMode = if (i == 0) AndroidTts.QUEUE_FLUSH else AndroidTts.QUEUE_ADD
                speakSingle(chunk, queueMode)
            }
        } finally {
            // Clear the karaoke-chunk when the whole response finishes so the UI
            // can fall back to showing the full response (lastResponse).
            _currentChunk.value = ""
        }
    }

    private suspend fun speakSingle(text: String, queueMode: Int) {
        // Long-form responses (multi-paragraph Japanese especially) need a
        // generous timeout: ~5cps JP speaking rate × ~3600 char chunks can
        // legitimately take ~3 minutes. Formula matches the revised
        // openclaw-assistant bounds: 30s base + 30ms/char, capped at 5 min.
        val timeoutMs = (30_000L + text.length * 30L).coerceAtMost(300_000L)

        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val utteranceId = UUID.randomUUID().toString()

                val listener = object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {
                        _isSpeaking.value = true
                        // Publish the active chunk so the UI can render a
                        // karaoke-style rolling display of just this sentence.
                        _currentChunk.value = text
                        Timber.d("TTS speaking: ${text.take(40)}...")
                    }
                    override fun onDone(id: String?) {
                        _isSpeaking.value = false
                        if (cont.isActive) cont.resume(Unit)
                    }
                    override fun onStop(id: String?, interrupted: Boolean) {
                        _isSpeaking.value = false
                        if (cont.isActive) cont.resume(Unit)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        Timber.e("TTS error for utterance: $id")
                        _isSpeaking.value = false
                        if (cont.isActive) cont.resume(Unit)
                    }
                }

                if (isInitialized) {
                    // Re-apply voice/locale/rate/pitch on every chunk so the
                    // engine cannot drift between chunks (some engines
                    // reset state between utterances, which would otherwise
                    // cause the second chunk to fall back to English).
                    setupVoice()
                    tts?.setOnUtteranceProgressListener(listener)
                    val result = tts?.speak(text, queueMode, null, utteranceId)
                    if (result != AndroidTts.SUCCESS) {
                        Timber.e("TTS speak() failed: $result")
                        _isSpeaking.value = false
                        if (cont.isActive) cont.resume(Unit)
                    }
                } else {
                    Timber.d("TTS not ready, queuing speak")
                    pendingSpeak = {
                        setupVoice()
                        tts?.setOnUtteranceProgressListener(listener)
                        tts?.speak(text, queueMode, null, utteranceId)
                    }
                }

                cont.invokeOnCancellation {
                    tts?.stop()
                    _isSpeaking.value = false
                    _currentChunk.value = ""
                }
            }
        }
    }

    override fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        _currentChunk.value = ""
    }

    fun shutdown() {
        tts?.shutdown()
        isInitialized = false
    }
}
