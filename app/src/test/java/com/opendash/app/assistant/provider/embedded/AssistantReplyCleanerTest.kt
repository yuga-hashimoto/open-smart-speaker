package com.opendash.app.assistant.provider.embedded

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Bug B regression: Gemma chat templates occasionally leak role markers
 * (e.g. the model starts a new `User:` turn) into the streamed output.
 * [AssistantReplyCleaner] strips those leading markers so TTS never
 * announces "User..." to the user.
 */
class AssistantReplyCleanerTest {

    @Test
    fun `bare User leaks strips to empty`() {
        assertThat(AssistantReplyCleaner.cleanContent("User")).isEmpty()
        assertThat(AssistantReplyCleaner.cleanContent("User...")).isEmpty()
        assertThat(AssistantReplyCleaner.cleanContent("user")).isEmpty()
    }

    @Test
    fun `User colon prefix is stripped but body kept`() {
        assertThat(AssistantReplyCleaner.cleanContent("User: hello there"))
            .isEqualTo("hello there")
        assertThat(AssistantReplyCleaner.cleanContent("USER: Hello"))
            .isEqualTo("Hello")
    }

    @Test
    fun `Assistant colon prefix is stripped but body kept`() {
        assertThat(AssistantReplyCleaner.cleanContent("Assistant: Hello"))
            .isEqualTo("Hello")
        assertThat(AssistantReplyCleaner.cleanContent("ASSISTANT: Sure thing."))
            .isEqualTo("Sure thing.")
    }

    @Test
    fun `chatml-style markers are stripped`() {
        assertThat(AssistantReplyCleaner.cleanContent("<|user|>\nHello"))
            .isEqualTo("Hello")
        assertThat(AssistantReplyCleaner.cleanContent("<|assistant|>\nResponse"))
            .isEqualTo("Response")
    }

    @Test
    fun `gemma turn markers are stripped`() {
        assertThat(
            AssistantReplyCleaner.cleanContent("<start_of_turn>user\nhello")
        ).isEqualTo("hello")
        assertThat(
            AssistantReplyCleaner.cleanContent("<start_of_turn>model\nReply")
        ).isEqualTo("Reply")
    }

    @Test
    fun `square bracket markers are stripped`() {
        assertThat(AssistantReplyCleaner.cleanContent("[USER] hi"))
            .isEqualTo("hi")
        assertThat(AssistantReplyCleaner.cleanContent("[assistant] ok"))
            .isEqualTo("ok")
    }

    @Test
    fun `angle bracket markers are stripped`() {
        assertThat(AssistantReplyCleaner.cleanContent("<user>hello"))
            .isEqualTo("hello")
        assertThat(AssistantReplyCleaner.cleanContent("<model>response"))
            .isEqualTo("response")
    }

    @Test
    fun `normal japanese text passes through unchanged`() {
        assertThat(AssistantReplyCleaner.cleanContent("東京は晴れです"))
            .isEqualTo("東京は晴れです")
    }

    @Test
    fun `normal english text passes through unchanged`() {
        assertThat(AssistantReplyCleaner.cleanContent("The weather is sunny."))
            .isEqualTo("The weather is sunny.")
    }

    @Test
    fun `blank input returns blank`() {
        assertThat(AssistantReplyCleaner.cleanContent("")).isEmpty()
        assertThat(AssistantReplyCleaner.cleanContent("   \n  ")).isEmpty()
    }

    @Test
    fun `multiple stacked markers get stripped`() {
        // Model sometimes emits `<|assistant|>\nUser:` etc.; we should strip
        // everything leading up to the first piece of real content.
        assertThat(
            AssistantReplyCleaner.cleanContent("<|assistant|>\nUser: hi")
        ).isEqualTo("hi")
    }

    @Test
    fun `text containing the word user in the middle is untouched`() {
        assertThat(
            AssistantReplyCleaner.cleanContent("The user guide is open.")
        ).isEqualTo("The user guide is open.")
    }
}
