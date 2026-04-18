package com.opendash.app.ui.common

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendash.app.util.NetworkMonitor
import com.opendash.app.voice.MicrophoneChecker
import com.opendash.app.voice.pipeline.VoicePipeline
import com.opendash.app.voice.pipeline.VoicePipelineState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModeScaffoldViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voicePipeline: VoicePipeline,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val voiceState: StateFlow<VoicePipelineState> = voicePipeline.state
    val partialText: StateFlow<String> = voicePipeline.partialText
    val lastResponse: StateFlow<String> = voicePipeline.lastResponse

    /**
     * The sentence currently being spoken by the TTS engine (karaoke-style).
     * Empty while idle or when the active provider does not stream chunks.
     * The UI shows this during [VoicePipelineState.Speaking] and falls back
     * to [lastResponse] otherwise.
     */
    val currentSpokenText: StateFlow<String> = voicePipeline.currentSpokenText
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    fun startVoiceInput() {
        if (!MicrophoneChecker.isMicrophoneAvailable(context)) {
            // Microphone hardware-blocked — show feedback
            voicePipeline.showError("Microphone is blocked. Check your device's privacy settings.")
            return
        }
        viewModelScope.launch {
            voicePipeline.startListening()
        }
    }
}
