package com.opensmarthome.speaker.voice.wakeword

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.jupiter.api.Test

/**
 * Pure unit tests for sensitivity gating. We do not spin up Vosk or AudioRecord —
 * the goal is to verify that the `isFinal` flag plus `config.sensitivity` gate
 * partial-result matching correctly.
 */
class VoskWakeWordDetectorTest {

    private fun detector(sensitivity: Float, keyword: String = "hey speaker"): VoskWakeWordDetector {
        val config = WakeWordConfig(keyword = keyword, sensitivity = sensitivity)
        return VoskWakeWordDetector(config, File("/does/not/matter"))
    }

    private fun partialJson(text: String) = """{"partial":"$text"}"""
    private fun finalJson(text: String) = """{"text":"$text"}"""

    @Test
    fun `final result fires callback regardless of low sensitivity`() {
        val d = detector(sensitivity = 0f)
        var fired = false
        d.start { fired = true }
        d.checkForWakeWord(finalJson("hey speaker please"), isFinal = true)
        assertThat(fired).isTrue()
    }

    @Test
    fun `partial result is gated when sensitivity is below threshold`() {
        val d = detector(sensitivity = 0.2f)
        var fired = false
        d.start { fired = true }
        d.checkForWakeWord(partialJson("hey speaker"), isFinal = false)
        assertThat(fired).isFalse()
    }

    @Test
    fun `partial result fires when sensitivity is above threshold`() {
        val d = detector(sensitivity = 0.9f)
        var fired = false
        d.start { fired = true }
        d.checkForWakeWord(partialJson("hey speaker now"), isFinal = false)
        assertThat(fired).isTrue()
    }

    @Test
    fun `non-matching text never fires even at max sensitivity`() {
        val d = detector(sensitivity = 1f)
        var fired = false
        d.start { fired = true }
        d.checkForWakeWord(finalJson("something unrelated"), isFinal = true)
        assertThat(fired).isFalse()
    }

    @Test
    fun `sensitivity threshold constant matches spec`() {
        // Document the contract so the UI slider's "0.5 = partial-match boundary" hint stays honest.
        assertThat(VoskWakeWordDetector.PARTIAL_MATCH_SENSITIVITY_THRESHOLD).isEqualTo(0.5f)
    }
}
