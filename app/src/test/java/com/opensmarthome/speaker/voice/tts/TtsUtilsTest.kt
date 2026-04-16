package com.opensmarthome.speaker.voice.tts

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TtsUtilsTest {

    // ---------- stripMarkdownForSpeech ----------

    @Test
    fun `strip preserves plain text`() {
        assertThat(TtsUtils.stripMarkdownForSpeech("Hello world")).isEqualTo("Hello world")
    }

    @Test
    fun `strip bold markers`() {
        assertThat(TtsUtils.stripMarkdownForSpeech("This is **bold** text"))
            .isEqualTo("This is bold text")
    }

    @Test
    fun `strip italic markers`() {
        assertThat(TtsUtils.stripMarkdownForSpeech("This is *italic* here"))
            .isEqualTo("This is italic here")
    }

    @Test
    fun `strip inline code`() {
        assertThat(TtsUtils.stripMarkdownForSpeech("Use `println` to print"))
            .isEqualTo("Use println to print")
    }

    @Test
    fun `strip headings`() {
        val input = """
            # Title
            ## Subtitle
            body
        """.trimIndent()
        val out = TtsUtils.stripMarkdownForSpeech(input)
        assertThat(out).contains("Title")
        assertThat(out).doesNotContain("#")
    }

    @Test
    fun `strip links keeps label`() {
        assertThat(TtsUtils.stripMarkdownForSpeech("See [the docs](https://example.com) now"))
            .isEqualTo("See the docs now")
    }

    @Test
    fun `strip bullets`() {
        val input = """
            - first item
            - second item
        """.trimIndent()
        val out = TtsUtils.stripMarkdownForSpeech(input)
        assertThat(out).contains("first item")
        assertThat(out).doesNotContain("- first")
    }

    @Test
    fun `strip code block fences`() {
        val input = """
            Here is some code:
            ```kotlin
            val x = 1
            ```
            done.
        """.trimIndent()
        val out = TtsUtils.stripMarkdownForSpeech(input)
        assertThat(out).doesNotContain("```")
        assertThat(out).contains("val x = 1")
    }

    @Test
    fun `strip gemma end_of_turn marker`() {
        assertThat(TtsUtils.stripMarkdownForSpeech("Thanks.<end_of_turn>"))
            .isEqualTo("Thanks.")
    }

    @Test
    fun `strip collapses triple-plus newlines to double`() {
        val input = "line1\n\n\n\nline2"
        val out = TtsUtils.stripMarkdownForSpeech(input)
        assertThat(out).isEqualTo("line1\n\nline2")
    }

    // ---------- splitIntoChunks ----------

    @Test
    fun `short text returns a single chunk`() {
        val chunks = TtsUtils.splitIntoChunks("Hello world.")
        assertThat(chunks).containsExactly("Hello world.")
    }

    @Test
    fun `long text is split at sentence boundaries`() {
        val input = "Sentence one. Sentence two. Sentence three. Sentence four."
        val chunks = TtsUtils.splitIntoChunks(input, maxChars = 20)
        assertThat(chunks.size).isGreaterThan(1)
        // Each chunk must end on a terminator.
        chunks.forEach { chunk ->
            assertThat(chunk.last()).isAnyOf('.', '!', '?', '。', '！', '？')
        }
    }

    @Test
    fun `japanese sentences split on fullwidth terminators`() {
        val input = "おはようございます。今日はいい天気ですね。散歩に行きませんか？"
        val chunks = TtsUtils.splitIntoChunks(input, maxChars = 15)
        assertThat(chunks.size).isAtLeast(2)
    }

    @Test
    fun `no sentence larger than maxChars stays whole`() {
        // Single extremely-long sentence — splitter keeps it as one chunk
        // rather than mid-sentence splitting.
        val input = "This is one very long sentence without any punctuation until the end here"
        val chunks = TtsUtils.splitIntoChunks(input, maxChars = 10)
        assertThat(chunks).hasSize(1)
    }
}
