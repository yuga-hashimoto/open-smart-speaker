package com.opensmarthome.speaker.voice.stt

import com.opensmarthome.speaker.data.preferences.AppPreferences
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import timber.log.Timber

/**
 * Routes [startListening] to one of several SpeechToText backends based on
 * the STT_PROVIDER preference. Lets the user pick between the Android system
 * recognizer (default, GMS-backed) and future offline backends (Vosk,
 * whisper.cpp) without replacing the singleton wired into the pipeline.
 *
 * The `isListening` flow proxies the currently-active delegate so the UI
 * doesn't need to know which backend handled the call.
 */
class DelegatingSttProvider(
    private val preferences: AppPreferences,
    private val android: SpeechToText,
    private val voskOffline: SpeechToText = OfflineSttStub("Vosk"),
    private val whisperOffline: SpeechToText = OfflineSttStub("Whisper")
) : SpeechToText {

    private val _listening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _listening.asStateFlow()

    @Volatile
    private var activeDelegate: SpeechToText = android

    override fun startListening(): Flow<SttResult> = flow {
        val raw = preferences.observe(PreferenceKeys.STT_PROVIDER_TYPE).firstOrNull()
        val type = SttProviderType.fromPref(raw)
        val delegate = delegateFor(type)
        activeDelegate = delegate
        _listening.value = true
        Timber.d("STT delegating to $type")
        try {
            delegate.startListening().collect { emit(it) }
        } finally {
            _listening.value = false
        }
    }

    override fun stopListening() {
        activeDelegate.stopListening()
        _listening.value = false
    }

    internal fun delegateFor(type: SttProviderType): SpeechToText = when (type) {
        SttProviderType.ANDROID -> android
        SttProviderType.VOSK_OFFLINE -> voskOffline
        SttProviderType.WHISPER_OFFLINE -> whisperOffline
    }

    /**
     * Exposes the Android backend so pipeline code can apply Android-specific
     * tuning (language, silence timeout) regardless of which provider is active.
     * Returns the raw delegate, not cast — callers pattern-match on the concrete
     * type they need.
     */
    fun androidDelegate(): SpeechToText = android
}
