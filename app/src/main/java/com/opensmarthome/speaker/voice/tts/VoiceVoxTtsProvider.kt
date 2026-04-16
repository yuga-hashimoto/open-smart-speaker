package com.opensmarthome.speaker.voice.tts

import android.content.Context
import android.media.MediaPlayer
import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import kotlin.coroutines.resume

/**
 * VOICEVOX Text-to-Speech provider (HTTP engine mode).
 *
 * Requires a running VOICEVOX ENGINE HTTP server (default http://localhost:50021).
 * User configures base URL in settings. The engine can run on a PC on the same LAN
 * or be embedded via voicevox-engine container.
 *
 * API flow:
 *   1. POST /audio_query?text=...&speaker={id}  → returns audio_query JSON
 *   2. POST /synthesis?speaker={id}  with audio_query body → WAV bytes
 *
 * Reference: OpenClaw Assistant VoiceVoxProvider (HTTP engine mode)
 * VOICEVOX docs: https://voicevox.github.io/voicevox_engine/api/
 */
class VoiceVoxTtsProvider(
    private val context: Context,
    private val preferences: AppPreferences,
    private val httpClient: OkHttpClient
) : TextToSpeech {

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    override suspend fun speak(text: String) {
        if (text.isBlank()) return

        val termsAccepted = preferences.observe(PreferenceKeys.VOICEVOX_TERMS_ACCEPTED).first() ?: false
        if (!termsAccepted) {
            Timber.w("VOICEVOX: terms not accepted, skipping")
            return
        }

        val baseUrl = preferences.observe(PreferenceKeys.VOICEVOX_BASE_URL).first()
            ?.takeIf { it.isNotBlank() }
            ?.trimEnd('/') ?: "http://localhost:50021"

        val speakerId = preferences.observe(PreferenceKeys.VOICEVOX_STYLE_ID).first() ?: 3 // ずんだもん ノーマル

        val cleanText = TtsUtils.stripMarkdownForSpeech(text)
        val encodedText = URLEncoder.encode(cleanText, "UTF-8")

        val audioFile = withContext(Dispatchers.IO) {
            try {
                // Step 1: audio_query
                val queryRequest = Request.Builder()
                    .url("$baseUrl/audio_query?text=$encodedText&speaker=$speakerId")
                    .post(ByteArray(0).toRequestBody())
                    .build()

                val queryJson = httpClient.newCall(queryRequest).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Timber.e("VOICEVOX audio_query failed: ${resp.code}")
                        return@withContext null
                    }
                    resp.body?.string()
                } ?: return@withContext null

                // Step 2: synthesis
                val synthRequest = Request.Builder()
                    .url("$baseUrl/synthesis?speaker=$speakerId")
                    .header("Content-Type", "application/json")
                    .header("Accept", "audio/wav")
                    .post(queryJson.toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(synthRequest).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Timber.e("VOICEVOX synthesis failed: ${resp.code}")
                        return@withContext null
                    }
                    val body = resp.body ?: return@withContext null
                    val f = File(context.cacheDir, "voicevox_tts_${System.currentTimeMillis()}.wav")
                    FileOutputStream(f).use { out -> body.byteStream().copyTo(out) }
                    f
                }
            } catch (e: Exception) {
                Timber.e(e, "VOICEVOX TTS request failed")
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
                Timber.e(e, "VOICEVOX MediaPlayer playback failed")
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
