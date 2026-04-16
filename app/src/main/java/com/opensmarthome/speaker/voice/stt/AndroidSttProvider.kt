package com.opensmarthome.speaker.voice.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale

/**
 * Android SpeechRecognizer wrapper.
 * Reference: OpenClaw Assistant SpeechRecognizerManager.kt
 *
 * Key design decisions from OpenClaw:
 * - Always destroy and recreate recognizer to avoid race conditions
 * - Run all recognizer operations on Main thread (Android requirement)
 * - Use foreground Activity context when available (Service context may fail)
 */
class AndroidSttProvider(private val context: Context) : SpeechToText {

    private var recognizer: SpeechRecognizer? = null
    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    var language: String? = null
    var silenceTimeoutMs: Long = 1500L

    override fun startListening(): Flow<SttResult> = callbackFlow {
        val targetLanguage = language ?: Locale.getDefault().toLanguageTag()

        Timber.d("STT: startListening (lang=$targetLanguage, silence=${silenceTimeoutMs}ms, available=${SpeechRecognizer.isRecognitionAvailable(context)})")

        // Destroy previous recognizer to avoid race conditions (OpenClaw pattern)
        withContext(Dispatchers.Main) {
            try {
                recognizer?.destroy()
            } catch (e: Exception) {
                Timber.w("Failed to destroy previous recognizer: ${e.message}")
            }
            recognizer = null

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Timber.e("Speech recognition not available on this device")
                trySend(SttResult.Error("Speech recognition not available"))
                close()
                return@withContext
            }

            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            Timber.d("STT: Created new recognizer instance")
        }

        val sr = recognizer
        if (sr == null) {
            trySend(SttResult.Error("Failed to create speech recognizer"))
            close()
            return@callbackFlow
        }

        // Set listener on Main thread (Android requirement)
        withContext(Dispatchers.Main) {
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                    Timber.d("STT: ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Timber.d("STT: speech started")
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _isListening.value = false
                    Timber.d("STT: end of speech")
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    val errorName = when (error) {
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
                        SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
                        SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
                        SpeechRecognizer.ERROR_SERVER -> "SERVER"
                        SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
                        SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RECOGNIZER_BUSY"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "INSUFFICIENT_PERMISSIONS"
                        else -> "UNKNOWN($error)"
                    }
                    Timber.w("STT error: $errorName")

                    // For critical errors, destroy recognizer (OpenClaw pattern)
                    val isSoftError = error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    if (!isSoftError) {
                        try { sr.destroy() } catch (_: Exception) {}
                        recognizer = null
                    }

                    trySend(SttResult.Error("SpeechRecognizer error: $errorName"))
                    close()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    val confidence = scores?.firstOrNull() ?: 1.0f
                    Timber.d("STT result: '$text' (confidence=$confidence)")
                    trySend(SttResult.Final(text, confidence))
                    close()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: return
                    trySend(SttResult.Partial(text))
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLanguage)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceTimeoutMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceTimeoutMs)
            // Unofficial extra for minimum speech length
            putExtra("android.speech.extras.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", silenceTimeoutMs)
        }

        // Start listening on Main thread (Android requirement)
        withContext(Dispatchers.Main) {
            try {
                sr.startListening(intent)
                Timber.d("STT: startListening() called")
            } catch (e: Exception) {
                Timber.e(e, "STT: startListening() failed")
                trySend(SttResult.Error("Failed to start speech recognition: ${e.message}"))
                close()
            }
        }

        awaitClose {
            Timber.d("STT: flow closing, cleaning up recognizer")
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
                try {
                    sr.cancel()
                    sr.destroy()
                } catch (e: Exception) {
                    Timber.w("STT cleanup failed: ${e.message}")
                }
            }
            recognizer = null
            _isListening.value = false
        }
    }

    override fun stopListening() {
        // No-op, flow cancellation triggers cleanup
    }
}
