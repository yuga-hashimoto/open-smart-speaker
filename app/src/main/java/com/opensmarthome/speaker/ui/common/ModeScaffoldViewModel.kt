package com.opensmarthome.speaker.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensmarthome.speaker.voice.pipeline.VoicePipeline
import com.opensmarthome.speaker.voice.pipeline.VoicePipelineState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModeScaffoldViewModel @Inject constructor(
    private val voicePipeline: VoicePipeline
) : ViewModel() {

    val voiceState: StateFlow<VoicePipelineState> = voicePipeline.state
    val partialText: StateFlow<String> = voicePipeline.partialText
    val lastResponse: StateFlow<String> = voicePipeline.lastResponse

    fun startVoiceInput() {
        viewModelScope.launch {
            voicePipeline.startListening()
        }
    }
}
