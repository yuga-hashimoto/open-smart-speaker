package com.opendash.app.voice.stt

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Placeholder for offline STT backends (Vosk / whisper.cpp) that are not yet
 * implemented. Emits a single `Error` result so the pipeline's ErrorClassifier
 * can surface a spoken-friendly "offline STT coming soon" message instead of
 * hanging. Real implementations replace this entirely.
 *
 * Rationale: keeping the interface live lets us ship the Settings UI for
 * provider selection and wire the routing code now, so future whisper.cpp /
 * Vosk PRs only touch implementation files.
 */
class OfflineSttStub(private val backendName: String) : SpeechToText {
    private val _listening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _listening.asStateFlow()

    override fun startListening(): Flow<SttResult> = flowOf(
        SttResult.Error("$backendName offline STT is not yet implemented. Falling back to system STT.")
    )

    override fun stopListening() {
        _listening.value = false
    }
}
