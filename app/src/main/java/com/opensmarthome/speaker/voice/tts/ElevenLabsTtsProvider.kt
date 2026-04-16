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
 * ElevenLabs Text-to-Speech provider.
 * POST https://api.elevenlabs.io/v1/text-to-speech/{voice_id}
 * Returns MP3 bytes → MediaPlayer.
 *
 * Reference: OpenClaw Assistant ElevenLabsTtsProvider
 */
class ElevenLabsTtsProvider(
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

        val apiKey = securePreferences.getString(SecurePreferences.KEY_ELEVENLABS_API_KEY)
        if (apiKey.isBlank()) {
            Timber.w("ElevenLabs TTS: API key not configured")
            return
        }

        val voiceId = preferences.observe(PreferenceKeys.ELEVENLABS_VOICE_ID).first()?.takeIf { it.isNotBlank() }
            ?: "21m00Tcm4TlvDq8ikWAM" // Rachel (default)
        val model = preferences.observe(PreferenceKeys.ELEVENLABS_MODEL).first()?.takeIf { it.isNotBlank() } ?: "eleven_multilingual_v2"
        val speed = preferences.observe(PreferenceKeys.ELEVENLABS_SPEED).first() ?: 1.0f

        val cleanText = TtsUtils.stripMarkdownForSpeech(text)

        val bodyJson = JSONObject().apply {
            put("text", cleanText)
            put("model_id", model)
            put("voice_settings", JSONObject().apply {
                put("stability", 0.5)
                put("similarity_boost", 0.75)
                put("style", 0.0)
                put("use_speaker_boost", true)
                put("speed", speed.coerceIn(0.7f, 1.2f))
            })
        }

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId")
            .header("xi-api-key", apiKey)
            .header("Accept", "audio/mpeg")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val audioFile = withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Timber.e("ElevenLabs HTTP ${resp.code}: ${resp.body?.string()}")
                        return@withContext null
                    }
                    val body = resp.body ?: return@withContext null
                    val f = File(context.cacheDir, "elevenlabs_tts_${System.currentTimeMillis()}.mp3")
                    FileOutputStream(f).use { out -> body.byteStream().copyTo(out) }
                    f
                }
            } catch (e: Exception) {
                Timber.e(e, "ElevenLabs TTS request failed")
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
