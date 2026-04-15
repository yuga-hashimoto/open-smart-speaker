package com.opensmarthome.speaker.voice.wakeword

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Vosk-based wake word detector.
 *
 * Requires Vosk model files to be present at [modelDir].
 * Uses Vosk's Recognizer to detect partial speech results containing the wake word keyword.
 *
 * Note: Vosk Android library must be included as a local AAR or from Maven.
 * The Vosk API is accessed via reflection to avoid compile-time dependency issues
 * with varying Vosk package names across versions.
 */
class VoskWakeWordDetector(
    private val config: WakeWordConfig,
    private val modelDir: File
) : WakeWordDetector {

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listeningJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var onDetectedCallback: (() -> Unit)? = null

    // Vosk objects held as Any to avoid compile-time dependency
    private var model: Any? = null
    private var recognizer: Any? = null

    private val keywordPattern by lazy {
        Regex(config.keyword.lowercase().trim())
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val RETRY_BACKOFF_BASE_MS = 1000L
        private const val RETRY_BACKOFF_MAX_MS = 10000L
        private const val WATCHDOG_TIMEOUT_MS = 5 * 60 * 1000L
    }

    override fun start(onDetected: () -> Unit) {
        if (_isListening.value) return
        onDetectedCallback = onDetected

        listeningJob = scope.launch {
            var retryCount = 0
            while (retryCount < MAX_RETRY_ATTEMPTS) {
                try {
                    initializeVosk()
                    startAudioLoop()
                    break
                } catch (e: Exception) {
                    retryCount++
                    Timber.e(e, "Vosk wake word detection failed (attempt $retryCount)")
                    val backoff = (RETRY_BACKOFF_BASE_MS * retryCount).coerceAtMost(RETRY_BACKOFF_MAX_MS)
                    delay(backoff)
                }
            }
            if (retryCount >= MAX_RETRY_ATTEMPTS) {
                Timber.e("Vosk wake word detection failed after $MAX_RETRY_ATTEMPTS attempts")
                _isListening.value = false
            }
        }
    }

    override fun stop() {
        listeningJob?.cancel()
        listeningJob = null
        releaseAudio()
        _isListening.value = false
    }

    private fun initializeVosk() {
        if (model != null) return

        if (!modelDir.exists() || !modelDir.isDirectory) {
            throw IllegalStateException(
                "Vosk model not found at: ${modelDir.absolutePath}. " +
                "Download a model from https://alphacephei.com/vosk/models"
            )
        }

        try {
            val modelClass = Class.forName("org.vosk.Model")
            model = modelClass.getConstructor(String::class.java).newInstance(modelDir.absolutePath)

            val recognizerClass = Class.forName("org.vosk.Recognizer")
            recognizer = recognizerClass.getConstructor(modelClass, Float::class.java)
                .newInstance(model, SAMPLE_RATE.toFloat())

            Timber.d("Vosk model loaded from: ${modelDir.absolutePath}")
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException(
                "Vosk library not found. Add vosk-android to your dependencies.", e
            )
        }
    }

    @Suppress("MissingPermission")
    private fun startAudioLoop() {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("Invalid audio buffer size: $bufferSize")
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord failed to initialize")
        }

        audioRecord!!.startRecording()
        _isListening.value = true
        Timber.d("Vosk wake word listening started for: '${config.keyword}'")

        val buffer = ShortArray(bufferSize / 2)
        val rec = recognizer ?: return
        val recClass = rec.javaClass
        val acceptMethod = recClass.getMethod("AcceptWaveform", ByteArray::class.java, Int::class.java)
        val resultMethod = recClass.getMethod("Result")
        val partialMethod = recClass.getMethod("PartialResult")

        while (_isListening.value) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
            if (read <= 0) continue

            val byteBuffer = ByteArray(read * 2)
            for (i in 0 until read) {
                byteBuffer[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                byteBuffer[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
            }

            val accepted = acceptMethod.invoke(rec, byteBuffer, byteBuffer.size) as Boolean
            val jsonResult = if (accepted) {
                resultMethod.invoke(rec) as String
            } else {
                partialMethod.invoke(rec) as String
            }
            checkForWakeWord(jsonResult)
        }
    }

    private fun checkForWakeWord(jsonResult: String) {
        try {
            val json = org.json.JSONObject(jsonResult)
            val text = json.optString("text", "")
                .ifBlank { json.optString("partial", "") }
                .lowercase()

            if (text.isNotBlank() && keywordPattern.containsMatchIn(text)) {
                Timber.d("Wake word detected in: '$text'")
                _isListening.value = false
                onDetectedCallback?.invoke()
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse Vosk result")
        }
    }

    private fun releaseAudio() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Timber.w(e, "Error releasing AudioRecord")
        }
    }
}
