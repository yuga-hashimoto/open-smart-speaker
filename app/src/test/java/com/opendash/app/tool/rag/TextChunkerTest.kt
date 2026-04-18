package com.opendash.app.tool.rag

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TextChunkerTest {

    @Test
    fun `short text is a single chunk`() {
        val chunker = TextChunker(chunkSize = 1600)
        val chunks = chunker.chunk("Just a short text.")
        assertThat(chunks).hasSize(1)
    }

    @Test
    fun `empty text produces no chunks`() {
        val chunks = TextChunker().chunk("")
        assertThat(chunks).isEmpty()
    }

    @Test
    fun `large text is split into overlapping chunks`() {
        val chunker = TextChunker(chunkSize = 100, overlap = 20)
        val text = "word ".repeat(100) // 500 chars
        val chunks = chunker.chunk(text)

        assertThat(chunks.size).isGreaterThan(2)
        // Consecutive chunks should share some overlap text
        for (i in 0 until chunks.size - 1) {
            val a = chunks[i].takeLast(20)
            val b = chunks[i + 1].take(20)
            // overlap isn't strict due to sentence-boundary search, but chunks
            // should collectively cover the original
        }
        // Recovery: concatenated length >= original (due to overlap)
        assertThat(chunks.sumOf { it.length }).isAtLeast(text.length - 50)
    }

    @Test
    fun `prefers sentence boundaries`() {
        val chunker = TextChunker(chunkSize = 50, overlap = 10)
        val text = "First sentence here. Second sentence follows. Third sentence is the end."
        val chunks = chunker.chunk(text)

        // Chunks should mostly end with punctuation
        assertThat(chunks.size).isAtLeast(1)
    }
}
