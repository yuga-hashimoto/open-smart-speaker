package com.opensmarthome.speaker.voice.tts

import android.content.Context
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.UUID
import kotlin.coroutines.resume
import android.speech.tts.TextToSpeech as AndroidTts

class AndroidTtsProvider(context: Context) : TextToSpeech {

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var tts: AndroidTts? = null
    private var isInitialized = false

    init {
        tts = AndroidTts(context) { status ->
            isInitialized = status == AndroidTts.SUCCESS
            if (!isInitialized) {
                Timber.e("TTS initialization failed: $status")
            }
        }
    }

    override suspend fun speak(text: String) {
        if (!isInitialized || text.isBlank()) return

        suspendCancellableCoroutine { cont ->
            val utteranceId = UUID.randomUUID().toString()
            _isSpeaking.value = true

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    _isSpeaking.value = false
                    if (cont.isActive) cont.resume(Unit)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    _isSpeaking.value = false
                    if (cont.isActive) cont.resume(Unit)
                }
            })

            tts?.speak(text, AndroidTts.QUEUE_FLUSH, null, utteranceId)

            cont.invokeOnCancellation {
                tts?.stop()
                _isSpeaking.value = false
            }
        }
    }

    override fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }
}
