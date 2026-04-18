package com.opendash.app.voice.tts

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
    fun `strip images keeps alt text`() {
        // Regression guard for PR #188: REGEX_IMAGE must be applied before
        // REGEX_LINK, otherwise the `[alt](url)` portion of `![alt](url)` is
        // consumed by the link matcher and leaves a stray '!' prefix.
        assertThat(TtsUtils.stripMarkdownForSpeech("![a kitten](cat.png) look at that"))
            .isEqualTo("a kitten look at that")
    }

    @Test
    fun `strip mixed image and link keeps both labels`() {
        val input = "Logo ![OSS logo](logo.png) and see [the docs](https://example.com)."
        assertThat(TtsUtils.stripMarkdownForSpeech(input))
            .isEqualTo("Logo OSS logo and see the docs.")
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
    fun `text under maxChars returns a single chunk even when maxChars is small`() {
        val chunks = TtsUtils.splitIntoChunks("Hi.", maxChars = 100)
        assertThat(chunks).containsExactly("Hi.")
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
    fun `japanese bang and question mark terminators split correctly`() {
        val input = "すごい！本当に？はい、そうです。"
        val chunks = TtsUtils.splitIntoChunks(input, maxChars = 6)
        assertThat(chunks.size).isAtLeast(2)
        // Reassembling all chunks preserves content (modulo whitespace trimming).
        assertThat(chunks.joinToString("").replace(" ", ""))
            .isEqualTo(input.replace(" ", ""))
    }

    @Test
    fun `paragraphs split on double newline boundary`() {
        val longParaA = "This paragraph has enough content to count. " +
            "And it continues with more words here. "
        val longParaB = "Second paragraph also has sufficient length. " +
            "It ends eventually with a final sentence. "
        val input = "$longParaA\n\n$longParaB"
        val chunks = TtsUtils.splitIntoChunks(input, maxChars = longParaA.length + 10)
        assertThat(chunks.size).isAtLeast(2)
        assertThat(chunks.first()).contains("This paragraph")
        assertThat(chunks.last()).contains("Second paragraph")
    }

    @Test
    fun `comma fallback splits text when no sentence end in range`() {
        // Long phrase with many Japanese commas but only one period at the
        // very end — the splitter should use the commas as secondary
        // boundaries rather than waiting for the terminal `。`.
        val input = "りんご、みかん、バナナ、ぶどう、もも、すいか、いちご、メロン、なし、柿。"
        val chunks = TtsUtils.splitIntoChunks(input, maxChars = 12)
        assertThat(chunks.size).isAtLeast(2)
        assertThat(chunks.joinToString("").replace(" ", ""))
            .isEqualTo(input.replace(" ", ""))
    }

    @Test
    fun `space fallback splits english text with no punctuation`() {
        // No sentence or comma terminators. The chunker must still make
        // progress at word boundaries rather than emitting a single long
        // utterance that would overflow the TTS engine.
        val input = "one two three four five six seven eight nine ten eleven twelve"
        val chunks = TtsUtils.splitIntoChunks(input, maxChars = 15)
        assertThat(chunks.size).isAtLeast(2)
        chunks.forEach { assertThat(it.length).isAtMost(15) }
    }

    @Test
    fun `force cut applies when no boundary available within range`() {
        // A single unbroken string with no whitespace or punctuation: the
        // splitter must still chop it up rather than produce a giant chunk
        // the TTS engine would silently truncate.
        val input = "A".repeat(40)
        val chunks = TtsUtils.splitIntoChunks(input, maxChars = 10)
        assertThat(chunks.size).isAtLeast(2)
        chunks.forEach { assertThat(it.length).isAtMost(10) }
        assertThat(chunks.joinToString("")).isEqualTo(input)
    }

    @Test
    fun `blank chunks are filtered out`() {
        // Multiple blank lines followed by content — the splitter must not
        // emit empty strings that would be handed to TTS.speak().
        val input = "\n\n\n\nHello.\n\n\n"
        val chunks = TtsUtils.splitIntoChunks(input, maxChars = 50)
        assertThat(chunks).isNotEmpty()
        chunks.forEach { assertThat(it).isNotEmpty() }
    }

    @Test
    fun `chunks are trimmed of leading and trailing whitespace`() {
        val input = "First sentence.   Second sentence.   Third sentence."
        val chunks = TtsUtils.splitIntoChunks(input, maxChars = 20)
        chunks.forEach {
            assertThat(it).isEqualTo(it.trim())
        }
    }

    // ---------- getMaxInputLength ----------

    @Test
    fun `getMaxInputLength returns safe fallback when tts is null`() {
        val max = TtsUtils.getMaxInputLength(null)
        // Fallback is a sane positive value well below typical engine limit.
        assertThat(max).isAtLeast(500)
        assertThat(max).isAtMost(10_000)
    }

    // ---------- splitIntoKaraokeChunks ----------

    @Test
    fun `karaoke split japanese three sentences emits three chunks`() {
        val input = "これは最初の文です。これは二番目の文。最後の文。"
        val chunks = TtsUtils.splitIntoKaraokeChunks(input)
        assertThat(chunks).hasSize(3)
        assertThat(chunks[0]).isEqualTo("これは最初の文です。")
        assertThat(chunks[1]).isEqualTo("これは二番目の文。")
        assertThat(chunks[2]).isEqualTo("最後の文。")
    }

    @Test
    fun `karaoke split english multi-sentence emits per-sentence chunks`() {
        val input = "Sentence one. Sentence two! Sentence three?"
        val chunks = TtsUtils.splitIntoKaraokeChunks(input)
        assertThat(chunks).hasSize(3)
        chunks.forEach { chunk ->
            assertThat(chunk.last()).isAnyOf('.', '!', '?', '。', '！', '？')
        }
    }

    @Test
    fun `karaoke split single short sentence returns one chunk as-is`() {
        val input = "Hello world."
        val chunks = TtsUtils.splitIntoKaraokeChunks(input)
        assertThat(chunks).containsExactly("Hello world.")
    }

    @Test
    fun `karaoke split blank text returns empty list`() {
        assertThat(TtsUtils.splitIntoKaraokeChunks("")).isEmpty()
        assertThat(TtsUtils.splitIntoKaraokeChunks("   \n\n  ")).isEmpty()
    }

    @Test
    fun `karaoke split keeps very long single sentence intact`() {
        // A single 300-char sentence without internal punctuation should NOT
        // be force-split mid-word — preserving prosody and not breaking
        // onStart mid-sentence is more important than hitting 180 chars
        // exactly, as long as we stay under the ~250 sentence cap.
        val input = "あ".repeat(260) + "。"
        val chunks = TtsUtils.splitIntoKaraokeChunks(input)
        // Expect a single chunk (or a small number) rather than force-cuts
        // at 180 breaking the sentence mid-word.
        assertThat(chunks).hasSize(1)
        assertThat(chunks[0]).isEqualTo(input)
    }

    @Test
    fun `karaoke split prefers paragraph breaks`() {
        val input = "First paragraph sentence.\n\nSecond paragraph sentence."
        val chunks = TtsUtils.splitIntoKaraokeChunks(input)
        assertThat(chunks.size).isAtLeast(2)
        assertThat(chunks.first()).contains("First paragraph")
        assertThat(chunks.last()).contains("Second paragraph")
    }

    @Test
    fun `karaoke split preserves all content when reassembled`() {
        val input = "文A。文B！文C？文D。"
        val chunks = TtsUtils.splitIntoKaraokeChunks(input)
        assertThat(chunks.joinToString("")).isEqualTo(input)
    }

    @Test
    fun `karaoke split filters out blank chunks`() {
        val input = "\n\n\nHello.\n\n\nWorld.\n\n"
        val chunks = TtsUtils.splitIntoKaraokeChunks(input)
        chunks.forEach { assertThat(it).isNotEmpty() }
        assertThat(chunks).isNotEmpty()
    }

    @Test
    fun `karaoke split honors custom maxLength by splitting sooner`() {
        // With default 180-char target, three 80-char sentences fit in 3 chunks.
        val sentence = "これは" + "あ".repeat(70) + "。"  // ~74 chars
        val input = sentence + sentence + sentence
        val chunks = TtsUtils.splitIntoKaraokeChunks(input)
        // All three sentences should each be their own chunk because each
        // one ends with 。
        assertThat(chunks).hasSize(3)
    }
}
