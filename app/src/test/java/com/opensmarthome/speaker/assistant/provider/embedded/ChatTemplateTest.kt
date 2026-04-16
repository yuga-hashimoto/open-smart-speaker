package com.opensmarthome.speaker.assistant.provider.embedded

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.assistant.model.AssistantMessage
import org.junit.jupiter.api.Test

class ChatTemplateTest {

    @Test
    fun `Gemma template uses start_of_turn markers`() {
        val out = GemmaTemplate.renderTurn(ChatTemplate.Role.USER, "Hi")
        assertThat(out).contains("<start_of_turn>user")
        assertThat(out).contains("<end_of_turn>")
    }

    @Test
    fun `Qwen template uses im_start markers`() {
        val out = QwenTemplate.renderTurn(ChatTemplate.Role.USER, "Hi")
        assertThat(out).contains("<|im_start|>user")
        assertThat(out).contains("<|im_end|>")
    }

    @Test
    fun `Llama3 template uses header_id markers`() {
        val out = Llama3Template.renderTurn(ChatTemplate.Role.USER, "Hi")
        assertThat(out).contains("<|start_header_id|>user<|end_header_id|>")
        assertThat(out).contains("<|eot_id|>")
    }

    @Test
    fun `forModelName picks Gemma by default`() {
        assertThat(ChatTemplate.forModelName(null)).isEqualTo(GemmaTemplate)
        assertThat(ChatTemplate.forModelName("gemma-3n-e2b")).isEqualTo(GemmaTemplate)
    }

    @Test
    fun `forModelName picks Qwen for qwen models`() {
        assertThat(ChatTemplate.forModelName("Qwen3-1.5B")).isEqualTo(QwenTemplate)
    }

    @Test
    fun `forModelName picks Llama3 for llama3 models`() {
        assertThat(ChatTemplate.forModelName("Llama-3.2-3B")).isEqualTo(Llama3Template)
        assertThat(ChatTemplate.forModelName("llama3-8b")).isEqualTo(Llama3Template)
    }

    @Test
    fun `renderMessages applies template to each turn`() {
        val messages = listOf(
            AssistantMessage.User(content = "Hello"),
            AssistantMessage.Assistant(content = "Hi there!"),
            AssistantMessage.User(content = "How are you?")
        )

        val rendered = QwenTemplate.renderMessages(messages)

        assertThat(rendered).contains("<|im_start|>user\nHello")
        assertThat(rendered).contains("<|im_start|>assistant\nHi there!")
        assertThat(rendered).contains("<|im_start|>user\nHow are you?")
    }

    @Test
    fun `renderMessages skips Delta messages`() {
        val messages = listOf(
            AssistantMessage.User(content = "Q"),
            AssistantMessage.Delta(contentDelta = "streaming..."),
            AssistantMessage.Assistant(content = "A")
        )

        val rendered = GemmaTemplate.renderMessages(messages)

        assertThat(rendered).doesNotContain("streaming")
    }

    @Test
    fun `Llama3 model turn marker is correct`() {
        assertThat(Llama3Template.modelTurnMarker()).contains("assistant")
    }
}
