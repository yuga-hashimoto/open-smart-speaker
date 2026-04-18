package com.opendash.app.voice.pipeline

sealed class VoicePipelineState {
    data object Idle : VoicePipelineState()
    data object WakeWordListening : VoicePipelineState()
    data object Listening : VoicePipelineState()
    data object Processing : VoicePipelineState()
    data object Thinking : VoicePipelineState()
    data object PreparingSpeech : VoicePipelineState()
    data object Speaking : VoicePipelineState()
    data class Error(val message: String) : VoicePipelineState()
}
