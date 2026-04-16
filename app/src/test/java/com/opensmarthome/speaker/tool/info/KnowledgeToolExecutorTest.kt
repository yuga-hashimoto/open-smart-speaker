package com.opensmarthome.speaker.tool.info

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.tool.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KnowledgeToolExecutorTest {

    private lateinit var store: InMemoryKnowledgeStore
    private lateinit var executor: KnowledgeToolExecutor

    @BeforeEach
    fun setup() {
        store = InMemoryKnowledgeStore()
        executor = KnowledgeToolExecutor(store)
    }

    @Test
    fun `availableTools has three knowledge tools`() = runTest {
        val names = executor.availableTools().map { it.name }
        assertThat(names).containsExactly("search_knowledge", "add_knowledge", "remove_knowledge")
    }

    @Test
    fun `add_knowledge stores entry`() = runTest {
        val result = executor.execute(
            ToolCall("1", "add_knowledge", mapOf(
                "question" to "What is the wifi password?",
                "answer" to "swordfish"
            ))
        )

        assertThat(result.success).isTrue()
        assertThat(store.listAll()).hasSize(1)
    }

    @Test
    fun `search_knowledge finds stored entries`() = runTest {
        executor.execute(
            ToolCall("1", "add_knowledge", mapOf(
                "question" to "What is the wifi password?",
                "answer" to "swordfish",
                "tags" to "wifi,network"
            ))
        )

        val result = executor.execute(
            ToolCall("2", "search_knowledge", mapOf("query" to "wifi"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("swordfish")
    }

    @Test
    fun `search_knowledge returns empty array for no match`() = runTest {
        val result = executor.execute(
            ToolCall("3", "search_knowledge", mapOf("query" to "nonexistent"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).isEqualTo("[]")
    }

    @Test
    fun `add_knowledge missing fields returns error`() = runTest {
        val result = executor.execute(
            ToolCall("4", "add_knowledge", mapOf("question" to "only question"))
        )

        assertThat(result.success).isFalse()
    }

    @Test
    fun `remove_knowledge deletes by id`() = runTest {
        val entry = KnowledgeEntry("myid", "Q", "A")
        store.add(entry)

        val result = executor.execute(
            ToolCall("5", "remove_knowledge", mapOf("id" to "myid"))
        )

        assertThat(result.success).isTrue()
        assertThat(store.listAll()).isEmpty()
    }

    @Test
    fun `search scores multi-term matches higher`() = runTest {
        store.add(KnowledgeEntry("a", "kitchen wifi", "only partial", emptyList()))
        store.add(KnowledgeEntry("b", "home wifi password", "perfect match", listOf("wifi")))

        val results = store.search("wifi password", limit = 10)

        // Entry b should be first — matches both terms + tag
        assertThat(results.first().id).isEqualTo("b")
    }
}
