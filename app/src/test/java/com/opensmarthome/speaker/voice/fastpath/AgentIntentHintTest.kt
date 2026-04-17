package com.opensmarthome.speaker.voice.fastpath

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Ensures ambiguous information-seeking utterances bypass fast-path and go to
 * the LLM, where multi-tool reasoning can happen. Explicit tool verbs (timer,
 * 検索, 予報, etc.) stay on the fast path.
 */
class AgentIntentHintTest {

    @Test
    fun `open information question is ambiguous`() {
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("トマトって何？")).isTrue()
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("量子コンピューターとは")).isTrue()
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("光合成について教えて")).isTrue()
    }

    @Test
    fun `english open question is ambiguous`() {
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("what is a black hole")).isTrue()
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("tell me about kotlin")).isTrue()
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("explain quantum computing")).isTrue()
    }

    @Test
    fun `explicit japanese tool verbs stay on fast path`() {
        // "検索" is explicit → not ambiguous
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("トマトを検索して")).isFalse()
        // "天気" is explicit → not ambiguous
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("天気教えて")).isFalse()
        // "予報" is explicit → not ambiguous
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("明日の予報")).isFalse()
        // "タイマー" is explicit command → not ambiguous
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("3分タイマー")).isFalse()
        // "ニュース" is explicit → not ambiguous
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("ニュース教えて")).isFalse()
    }

    @Test
    fun `explicit english tool verbs stay on fast path`() {
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("search for tomatoes")).isFalse()
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("weather today")).isFalse()
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("forecast for tomorrow")).isFalse()
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("set timer 3 minutes")).isFalse()
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("news please")).isFalse()
    }

    @Test
    fun `commands with imperative verbs are not ambiguous`() {
        // Even though "what about X" could look ambiguous, "turn on" is a command.
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("turn on the lights")).isFalse()
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("電気つけて")).isFalse()
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("音量を50に")).isFalse()
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("volume up")).isFalse()
    }

    @Test
    fun `short greetings are not ambiguous info queries`() {
        // Greetings are handled by GreetingMatcher; we only fire on info queries.
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("hello")).isFalse()
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("hi there")).isFalse()
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("こんにちは")).isFalse()
    }

    @Test
    fun `empty or whitespace returns false`() {
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("")).isFalse()
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("   ")).isFalse()
    }

    @Test
    fun `howToQuestions are ambiguous`() {
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("how do I make pasta")).isTrue()
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("why is the sky blue")).isTrue()
        assertThat(AgentIntentHint.isAmbiguousInformationQuery("パスタの作り方を教えて")).isTrue()
    }

    @Test
    fun `compound query with explicit tool verb overrides ambiguity`() {
        // User says both "教えて" (ambiguous hint) and "予報" (explicit tool).
        // Explicit tool wins so the fast-path can handle it.
        assertThat(
            AgentIntentHint.isAmbiguousInformationQuery("明日の予報を教えて")
        ).isFalse()
    }
}
