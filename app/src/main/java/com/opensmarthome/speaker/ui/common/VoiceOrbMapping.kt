package com.opensmarthome.speaker.ui.common

import com.opensmarthome.speaker.voice.pipeline.VoicePipelineState

/**
 * Adapter so screens don't import VoicePipelineState directly and
 * can bind VoiceOrb to any source of pipeline-like state.
 */
fun VoicePipelineState.toOrbState(): VoiceOrbState = when (this) {
    is VoicePipelineState.Idle -> VoiceOrbState.Idle
    is VoicePipelineState.WakeWordListening -> VoiceOrbState.WakeWordListening
    is VoicePipelineState.Listening -> VoiceOrbState.Listening
    is VoicePipelineState.Processing -> VoiceOrbState.Processing
    is VoicePipelineState.Thinking -> VoiceOrbState.Thinking
    is VoicePipelineState.PreparingSpeech -> VoiceOrbState.PreparingSpeech
    is VoicePipelineState.Speaking -> VoiceOrbState.Speaking
    is VoicePipelineState.Error -> VoiceOrbState.Error
}
