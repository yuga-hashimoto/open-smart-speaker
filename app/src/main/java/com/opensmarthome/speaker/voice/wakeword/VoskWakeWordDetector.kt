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
 * Reference: OpenClaw Assistant HotwordService.kt
 *
 * Key patterns from OpenClaw:
 * - Pause hotword detection when STT session is active (prevent mic conflict)
 * - Resume after session ends
 * - Exponential backoff on failure
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
    @Volatile
    private var isPaused = false

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
        internal const val PARTIAL_MATCH_SENSITIVITY_THRESHOLD = 0.5f
        private val FINAL_TEXT_REGEX = Regex("\"text\"\\s*:\\s*\"([^\"]*)\"")
        private val PARTIAL_TEXT_REGEX = Regex("\"partial\"\\s*:\\s*\"([^\"]*)\"")
    }

    override fun start(onDetected: () -> Unit) {
        if (_isListening.value) return
        onDetectedCallback = onDetected
        isPaused = false

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
        // Order matters: set flags FIRST so the audio loop exits cleanly,
        // then release the AudioRecord to unblock any in-flight read().
        isPaused = true
        _isListening.value = false
        listeningJob?.cancel()
        listeningJob = null
        releaseAudio()
    }

    /**
     * Pause wake word detection to release the microphone for STT.
     * Reference: OpenClaw Assistant ACTION_PAUSE_HOTWORD broadcast pattern.
     */
    fun pause() {
        Timber.d("Vosk: pausing wake word detection")
        isPaused = true
        _isListening.value = false
        // Cancel the listening job to force-stop the blocking read loop
        listeningJob?.cancel()
        listeningJob = null
        releaseAudio()
    }

    /**
     * Resume wake word detection after STT session ends.
     * Reference: OpenClaw Assistant ACTION_RESUME_HOTWORD broadcast pattern.
     */
    fun resume() {
        if (!isPaused) return
        Timber.d("Vosk: resuming wake word detection")
        isPaused = false
        if (onDetectedCallback != null) {
            start(onDetectedCallback!!)
        }
    }

    private fun initializeVosk() {
        if (model != null) return

        if (!modelDir.exists() || !modelDir.isDirectory) {
            throw IllegalStateException("Vosk model not found at: ${modelDir.absolutePath}")
        }

        try {
            val modelClass = Class.forName("org.vosk.Model")
            model = modelClass.getConstructor(String::class.java).newInstance(modelDir.absolutePath)

            val recognizerClass = Class.forName("org.vosk.Recognizer")
            recognizer = recognizerClass.getConstructor(modelClass, Float::class.java)
                .newInstance(model, SAMPLE_RATE.toFloat())

            Timber.d("Vosk model loaded from: ${modelDir.absolutePath}")
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException("Vosk library not found.", e)
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
        Timber.d("Vosk wake word listening for: '${config.keyword}'")

        val buffer = ShortArray(bufferSize / 2)
        val rec = recognizer ?: return
        val recClass = rec.javaClass

        // Vosk 0.3.47 uses lowercase method names: acceptWaveForm, getResult, getPartialResult
        val acceptMethod = try {
            recClass.getMethod("acceptWaveForm", ByteArray::class.java, Int::class.javaPrimitiveType)
        } catch (e: NoSuchMethodException) {
            try {
                recClass.getMethod("AcceptWaveform", ByteArray::class.java, Int::class.javaPrimitiveType)
            } catch (e2: NoSuchMethodException) {
                // Try short[] variant
                recClass.getMethod("acceptWaveForm", ShortArray::class.java, Int::class.javaPrimitiveType)
            }
        }

        val resultMethod = try {
            recClass.getMethod("getResult")
        } catch (e: NoSuchMethodException) {
            recClass.getMethod("Result")
        }

        val partialMethod = try {
            recClass.getMethod("getPartialResult")
        } catch (e: NoSuchMethodException) {
            recClass.getMethod("PartialResult")
        }

        Timber.d("Vosk methods resolved: accept=${acceptMethod.name}, result=${resultMethod.name}, partial=${partialMethod.name}")

        try {
            while (_isListening.value && !isPaused) {
                val ar = audioRecord ?: break
                val read = try {
                    ar.read(buffer, 0, buffer.size)
                } catch (e: IllegalStateException) {
                    Timber.d("Vosk: AudioRecord read threw (likely released) — exiting loop")
                    break
                }
                // Negative value = error (e.g. ERROR_DEAD_OBJECT after release).
                // Break instead of continue to avoid tight infinite loop that holds mic.
                if (read < 0) {
                    Timber.d("Vosk: AudioRecord read returned error $read — exiting loop")
                    break
                }
                if (read == 0) continue

                // Check if we're using byte[] or short[] variant
                val accepted = if (acceptMethod.parameterTypes[0] == ShortArray::class.java) {
                    acceptMethod.invoke(rec, buffer, read) as Boolean
                } else {
                    val byteBuffer = ByteArray(read * 2)
                    for (i in 0 until read) {
                        byteBuffer[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
                    }
                    acceptMethod.invoke(rec, byteBuffer, byteBuffer.size) as Boolean
                }

                val jsonResult = if (accepted) {
                    resultMethod.invoke(rec) as String
                } else {
                    partialMethod.invoke(rec) as String
                }
                checkForWakeWord(jsonResult, isFinal = accepted)
            }
        } catch (e: Exception) {
            Timber.w(e, "Vosk audio loop exited with exception")
        } finally {
            releaseAudio()
            _isListening.value = false
        }
    }

    /**
     * Sensitivity gates partial-result matching: when sensitivity < 0.5, only final
     * (end-of-utterance) Vosk results are considered. This reduces false wakes at
     * the cost of a ~300ms latency penalty vs. matching on partials.
     *
     * JSON is parsed via regex instead of org.json.JSONObject because the Android
     * stdlib mock used during JVM unit tests throws on JSONObject construction.
     * Vosk's output is simple {"text":"..."} / {"partial":"..."} so this is safe.
     */
    internal fun checkForWakeWord(jsonResult: String, isFinal: Boolean) {
        if (!isFinal && config.sensitivity < PARTIAL_MATCH_SENSITIVITY_THRESHOLD) return
        try {
            val text = extractVoskText(jsonResult)?.lowercase() ?: return

            if (text.isNotBlank() && keywordPattern.containsMatchIn(text)) {
                Timber.d("Wake word detected in: '$text' (isFinal=$isFinal)")
                // Pause before triggering callback to release mic
                pause()
                onDetectedCallback?.invoke()
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse Vosk result")
        }
    }

    private fun extractVoskText(jsonResult: String): String? {
        val finalMatch = FINAL_TEXT_REGEX.find(jsonResult)?.groupValues?.getOrNull(1)
        if (!finalMatch.isNullOrBlank()) return finalMatch
        return PARTIAL_TEXT_REGEX.find(jsonResult)?.groupValues?.getOrNull(1)
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
