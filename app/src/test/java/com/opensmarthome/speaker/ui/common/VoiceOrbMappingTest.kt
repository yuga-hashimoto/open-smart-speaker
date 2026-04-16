package com.opensmarthome.speaker.ui.common

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.voice.pipeline.VoicePipelineState
import org.junit.jupiter.api.Test

class VoiceOrbMappingTest {

    @Test
    fun `Idle maps to Idle`() {
        assertThat(VoicePipelineState.Idle.toOrbState()).isEqualTo(VoiceOrbState.Idle)
    }

    @Test
    fun `WakeWordListening maps to WakeWordListening`() {
        assertThat(VoicePipelineState.WakeWordListening.toOrbState())
            .isEqualTo(VoiceOrbState.WakeWordListening)
    }

    @Test
    fun `Listening maps to Listening`() {
        assertThat(VoicePipelineState.Listening.toOrbState()).isEqualTo(VoiceOrbState.Listening)
    }

    @Test
    fun `Processing maps to Processing`() {
        assertThat(VoicePipelineState.Processing.toOrbState()).isEqualTo(VoiceOrbState.Processing)
    }

    @Test
    fun `Thinking maps to Thinking`() {
        assertThat(VoicePipelineState.Thinking.toOrbState()).isEqualTo(VoiceOrbState.Thinking)
    }

    @Test
    fun `PreparingSpeech maps to PreparingSpeech`() {
        assertThat(VoicePipelineState.PreparingSpeech.toOrbState())
            .isEqualTo(VoiceOrbState.PreparingSpeech)
    }

    @Test
    fun `Speaking maps to Speaking`() {
        assertThat(VoicePipelineState.Speaking.toOrbState()).isEqualTo(VoiceOrbState.Speaking)
    }

    @Test
    fun `Error maps to Error`() {
        assertThat(VoicePipelineState.Error("boom").toOrbState()).isEqualTo(VoiceOrbState.Error)
    }
}
