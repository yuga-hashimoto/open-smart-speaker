package com.opendash.app.tool.info

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class InMemoryKnowledgeStoreTest {

    private fun store(vararg entries: KnowledgeEntry) = InMemoryKnowledgeStore(entries.toList())

    @Test
    fun `empty store returns empty list`() = runTest {
        val s = InMemoryKnowledgeStore()
        assertThat(s.search("anything")).isEmpty()
        assertThat(s.listAll()).isEmpty()
    }

    @Test
    fun `add assigns id when blank`() = runTest {
        val s = InMemoryKnowledgeStore()
        s.add(KnowledgeEntry(id = "", question = "What", answer = "A"))
        val all = s.listAll()
        assertThat(all).hasSize(1)
        assertThat(all[0].id).isNotEmpty()
    }

    @Test
    fun `add preserves explicit id`() = runTest {
        val s = InMemoryKnowledgeStore()
        s.add(KnowledgeEntry(id = "fixed-id", question = "Q", answer = "A"))
        assertThat(s.listAll()[0].id).isEqualTo("fixed-id")
    }

    @Test
    fun `add with same id overwrites`() = runTest {
        val s = InMemoryKnowledgeStore()
        s.add(KnowledgeEntry("k1", "v1", "first"))
        s.add(KnowledgeEntry("k1", "v1", "second"))
        assertThat(s.listAll()).hasSize(1)
        assertThat(s.listAll()[0].answer).isEqualTo("second")
    }

    @Test
    fun `remove returns true when present`() = runTest {
        val s = InMemoryKnowledgeStore()
        s.add(KnowledgeEntry("id-x", "q", "a"))
        assertThat(s.remove("id-x")).isTrue()
        assertThat(s.listAll()).isEmpty()
    }

    @Test
    fun `remove returns false when missing`() = runTest {
        val s = InMemoryKnowledgeStore()
        assertThat(s.remove("nope")).isFalse()
    }

    @Test
    fun `search matches against question answer and tags`() = runTest {
        val s = store(
            KnowledgeEntry("1", "How do I brew coffee?", "Use beans and hot water.", listOf("coffee", "drink")),
            KnowledgeEntry("2", "Best tea brands?", "Try Harney and Sons.", listOf("tea", "drink")),
            KnowledgeEntry("3", "Is bread hard to make?", "Start with a simple loaf.", listOf("bread", "cooking"))
        )
        val hits = s.search("coffee")
        assertThat(hits).hasSize(1)
        assertThat(hits[0].id).isEqualTo("1")
    }

    @Test
    fun `search is case insensitive`() = runTest {
        val s = store(
            KnowledgeEntry("1", "Tesla charging", "CCS plug", listOf("EV"))
        )
        val hits = s.search("TESLA")
        assertThat(hits.map { it.id }).containsExactly("1")
    }

    @Test
    fun `search ranks by term-match count`() = runTest {
        val s = store(
            KnowledgeEntry("high", "coffee beans roasting guide", "Dark roast for espresso.", listOf("coffee", "roasting")),
            KnowledgeEntry("low", "tea basics", "Boil water.", listOf("tea"))
        )
        val hits = s.search("coffee roasting beans")
        assertThat(hits.first().id).isEqualTo("high")
    }

    @Test
    fun `search respects limit`() = runTest {
        val s = store(
            KnowledgeEntry("1", "cat", "a", listOf()),
            KnowledgeEntry("2", "cat", "b", listOf()),
            KnowledgeEntry("3", "cat", "c", listOf())
        )
        assertThat(s.search("cat", limit = 2)).hasSize(2)
    }

    @Test
    fun `search with blank query returns empty`() = runTest {
        val s = store(KnowledgeEntry("1", "q", "a"))
        assertThat(s.search("   ")).isEmpty()
    }

    @Test
    fun `initial entries are indexed`() = runTest {
        val seed = listOf(
            KnowledgeEntry("p", "pizza", "italian", listOf())
        )
        val s = InMemoryKnowledgeStore(seed)
        assertThat(s.listAll().map { it.id }).containsExactly("p")
        assertThat(s.search("pizza")).hasSize(1)
    }
}
