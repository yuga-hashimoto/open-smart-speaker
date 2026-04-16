package com.opensmarthome.speaker.voice.tts

import android.content.Context
import android.media.MediaPlayer
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import com.opensmarthome.speaker.data.preferences.SecurePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

/**
 * OpenAI Text-to-Speech provider.
 * POST /v1/audio/speech → MP3 bytes → MediaPlayer playback.
 *
 * Reference: OpenClaw Assistant OpenAITtsProvider
 * API docs: https://platform.openai.com/docs/api-reference/audio/createSpeech
 */
class OpenAiTtsProvider(
    private val context: Context,
    private val preferences: AppPreferences,
    private val securePreferences: SecurePreferences,
    private val httpClient: OkHttpClient
) : TextToSpeech {

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    override suspend fun speak(text: String) {
        if (text.isBlank()) return

        val apiKey = securePreferences.getString(SecurePreferences.KEY_OPENAI_TTS_API_KEY)
        if (apiKey.isBlank()) {
            Timber.w("OpenAI TTS: API key not configured")
            return
        }

        val voice = preferences.observe(PreferenceKeys.OPENAI_TTS_VOICE).first()?.takeIf { it.isNotBlank() } ?: "alloy"
        val model = preferences.observe(PreferenceKeys.OPENAI_TTS_MODEL).first()?.takeIf { it.isNotBlank() } ?: "tts-1"
        val speed = preferences.observe(PreferenceKeys.TTS_SPEECH_RATE).first() ?: 1.0f

        val cleanText = TtsUtils.stripMarkdownForSpeech(text)

        val bodyJson = JSONObject().apply {
            put("model", model)
            put("input", cleanText)
            put("voice", voice)
            put("speed", speed.coerceIn(0.25f, 4.0f))
            put("response_format", "mp3")
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/speech")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val audioFile = withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Timber.e("OpenAI TTS HTTP ${resp.code}: ${resp.body?.string()}")
                        return@withContext null
                    }
                    val body = resp.body ?: return@withContext null
                    val f = File(context.cacheDir, "openai_tts_${System.currentTimeMillis()}.mp3")
                    FileOutputStream(f).use { out -> body.byteStream().copyTo(out) }
                    f
                }
            } catch (e: Exception) {
                Timber.e(e, "OpenAI TTS request failed")
                null
            }
        } ?: return

        playFile(audioFile)
        audioFile.delete()
    }

    private suspend fun playFile(file: File) {
        suspendCancellableCoroutine<Unit> { cont ->
            try {
                stop()
                val mp = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener {
                        _isSpeaking.value = false
                        if (cont.isActive) cont.resume(Unit)
                    }
                    setOnErrorListener { _, what, extra ->
                        Timber.e("MediaPlayer error: what=$what extra=$extra")
                        _isSpeaking.value = false
                        if (cont.isActive) cont.resume(Unit)
                        true
                    }
                    prepare()
                    start()
                }
                mediaPlayer = mp
                _isSpeaking.value = true

                cont.invokeOnCancellation {
                    try { mp.stop() } catch (_: Exception) {}
                    try { mp.release() } catch (_: Exception) {}
                    _isSpeaking.value = false
                }
            } catch (e: Exception) {
                Timber.e(e, "MediaPlayer playback failed")
                _isSpeaking.value = false
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    override fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _isSpeaking.value = false
    }
}
