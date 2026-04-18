package com.opendash.app.tool.memory

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TfIdfIndexTest {

    @Test
    fun `tokenize splits and normalizes`() {
        val tokens = TfIdfIndex.tokenize("Hello, World! The quick brown fox.")
        assertThat(tokens).containsAtLeast("hello", "world", "quick", "brown", "fox")
        // Stop words filtered
        assertThat(tokens).doesNotContain("the")
    }

    @Test
    fun `search returns exact match first`() {
        val index = TfIdfIndex(listOf(
            TfIdfIndex.Document("a", "The cat sat on the mat"),
            TfIdfIndex.Document("b", "Dogs bark loudly at night"),
            TfIdfIndex.Document("c", "A small cat chased a butterfly")
        ))

        val hits = index.search("cat", limit = 3)

        assertThat(hits.map { it.document.id }).contains("a")
        assertThat(hits.map { it.document.id }).contains("c")
        assertThat(hits.first().score).isGreaterThan(0.0)
    }

    @Test
    fun `search scores multi-term matches higher`() {
        val index = TfIdfIndex(listOf(
            TfIdfIndex.Document("a", "cat only"),
            TfIdfIndex.Document("b", "cat and dog both"),
            TfIdfIndex.Document("c", "dog alone")
        ))

        val hits = index.search("cat dog", limit = 3)

        assertThat(hits.first().document.id).isEqualTo("b")
    }

    @Test
    fun `unknown query returns empty`() {
        val index = TfIdfIndex(listOf(
            TfIdfIndex.Document("a", "hello world")
        ))

        val hits = index.search("xyzzy", limit = 5)
        assertThat(hits).isEmpty()
    }

    @Test
    fun `empty index returns empty`() {
        val index = TfIdfIndex(emptyList())
        assertThat(index.search("anything", limit = 5)).isEmpty()
    }

    @Test
    fun `relevant match beats unrelated document`() {
        val index = TfIdfIndex(listOf(
            TfIdfIndex.Document("a", "user.birthday May 3rd"),
            TfIdfIndex.Document("b", "preference.theme dark"),
            TfIdfIndex.Document("c", "pet.name Rex")
        ))

        val hits = index.search("when is the birthday", limit = 3)

        assertThat(hits).isNotEmpty()
        assertThat(hits.first().document.id).isEqualTo("a")
    }
}
