package com.opensmarthome.speaker.ui.common

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensmarthome.speaker.util.NetworkMonitor
import com.opensmarthome.speaker.voice.MicrophoneChecker
import com.opensmarthome.speaker.voice.pipeline.VoicePipeline
import com.opensmarthome.speaker.voice.pipeline.VoicePipelineState
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
