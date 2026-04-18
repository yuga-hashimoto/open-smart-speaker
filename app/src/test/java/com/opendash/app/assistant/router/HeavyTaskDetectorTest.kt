package com.opendash.app.assistant.router

import com.google.common.truth.Truth.assertThat
import com.opendash.app.assistant.provider.ProviderCapabilities
import org.junit.jupiter.api.Test

class HeavyTaskDetectorTest {

    private val textOnlyLocal = ProviderCapabilities(
        supportsStreaming = true,
        supportsTools = true,
        maxContextTokens = 4096,
        modelName = "gemma-4-e2b",
        supportsVision = false,
        isLocal = true
    )

    private val visionLocal = textOnlyLocal.copy(supportsVision = true)

    @Test
    fun `short simple query stays local`() {
        val d = HeavyTaskDetector.decide("what time is it", textOnlyLocal)
        assertThat(d.escalate).isFalse()
    }

    @Test
    fun `empty input does not escalate`() {
        val d = HeavyTaskDetector.decide("   ", textOnlyLocal)
        assertThat(d.escalate).isFalse()
    }

    @Test
    fun `long input escalates`() {
        val input = (1..100).joinToString(" ") { "word$it" }
        val d = HeavyTaskDetector.decide(input, textOnlyLocal)
        assertThat(d.escalate).isTrue()
        assertThat(d.reason).contains("long input")
    }

    @Test
    fun `write an essay escalates`() {
        val d = HeavyTaskDetector.decide("Please write an essay about climate", textOnlyLocal)
        assertThat(d.escalate).isTrue()
        assertThat(d.reason).contains("heavy keyword")
    }

    @Test
    fun `step by step explanation escalates`() {
        val d = HeavyTaskDetector.decide("Explain step by step how photosynthesis works", textOnlyLocal)
        assertThat(d.escalate).isTrue()
    }

    @Test
    fun `image request without vision escalates`() {
        val d = HeavyTaskDetector.decide("What's in this photo?", textOnlyLocal)
        assertThat(d.escalate).isTrue()
        assertThat(d.reason).contains("vision")
    }

    @Test
    fun `image request with vision stays local`() {
        val d = HeavyTaskDetector.decide("What's in this photo?", visionLocal)
        assertThat(d.escalate).isFalse()
    }

    @Test
    fun `japanese essay request escalates`() {
        val d = HeavyTaskDetector.decide("気候変動についてエッセイを書いてください", textOnlyLocal)
        assertThat(d.escalate).isTrue()
    }

    @Test
    fun `japanese image request without vision escalates`() {
        val d = HeavyTaskDetector.decide("この写真に何が写っていますか", textOnlyLocal)
        assertThat(d.escalate).isTrue()
    }

    @Test
    fun `compare and contrast escalates`() {
        val d = HeavyTaskDetector.decide("Compare and contrast jazz and blues", textOnlyLocal)
        assertThat(d.escalate).isTrue()
        assertThat(d.reason).contains("heavy keyword")
    }

    @Test
    fun `write a function escalates`() {
        val d = HeavyTaskDetector.decide("Write a function to sort a list", textOnlyLocal)
        assertThat(d.escalate).isTrue()
    }

    @Test
    fun `plan a trip escalates`() {
        val d = HeavyTaskDetector.decide("Plan a trip to Kyoto for next month", textOnlyLocal)
        assertThat(d.escalate).isTrue()
    }

    @Test
    fun `deep dive escalates`() {
        val d = HeavyTaskDetector.decide("Give me a deep dive on transformers", textOnlyLocal)
        assertThat(d.escalate).isTrue()
    }

    @Test
    fun `japanese travel plan escalates`() {
        val d = HeavyTaskDetector.decide("京都への旅行計画を立てて", textOnlyLocal)
        assertThat(d.escalate).isTrue()
    }

    @Test
    fun `japanese function generation escalates`() {
        val d = HeavyTaskDetector.decide("ソートする関数を書いてください", textOnlyLocal)
        assertThat(d.escalate).isTrue()
    }
}
