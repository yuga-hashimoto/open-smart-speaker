package com.opensmarthome.speaker.tool.memory

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.data.db.MemoryDao
import com.opensmarthome.speaker.data.db.MemoryEntity
import com.opensmarthome.speaker.tool.ToolCall
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MemoryToolExecutorTest {

    private lateinit var executor: MemoryToolExecutor
    private lateinit var dao: MemoryDao

    @BeforeEach
    fun setup() {
        dao = mockk(relaxed = true)
        executor = MemoryToolExecutor(dao)
    }

    @Test
    fun `availableTools exposes all memory tools`() = runTest {
        val names = executor.availableTools().map { it.name }
        assertThat(names).containsExactly(
            "remember", "recall", "search_memory", "forget", "semantic_memory_search", "list_memory"
        )
    }

    @Test
    fun `remember persists key-value`() = runTest {
        val result = executor.execute(
            ToolCall("1", "remember", mapOf("key" to "user.name", "value" to "Alice"))
        )

        assertThat(result.success).isTrue()
        coVerify { dao.upsert(match { it.key == "user.name" && it.value == "Alice" }) }
    }

    @Test
    fun `remember missing fields returns error`() = runTest {
        val result = executor.execute(
            ToolCall("2", "remember", mapOf("key" to "k"))
        )

        assertThat(result.success).isFalse()
    }

    @Test
    fun `list_memory without prefix lists recent`() = runTest {
        coEvery { dao.list(20) } returns listOf(
            MemoryEntity("a", "v1", 1L),
            MemoryEntity("b", "v2", 2L)
        )
        val result = executor.execute(ToolCall("3", "list_memory", emptyMap()))

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"key\":\"a\"")
        assertThat(result.data).contains("\"key\":\"b\"")
    }

    @Test
    fun `list_memory with prefix filters by prefix`() = runTest {
        coEvery { dao.listByPrefix("user.", 20) } returns listOf(
            MemoryEntity("user.name", "Alice", 1L)
        )
        val result = executor.execute(
            ToolCall("4", "list_memory", mapOf("prefix" to "user."))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"key\":\"user.name\"")
        coVerify { dao.listByPrefix("user.", 20) }
    }

    @Test
    fun `list_memory clamps limit`() = runTest {
        coEvery { dao.list(100) } returns emptyList()
        executor.execute(
            ToolCall("5", "list_memory", mapOf("limit" to 999))
        )
        coVerify { dao.list(100) }
    }

    @Test
    fun `recall returns stored value`() = runTest {
        coEvery { dao.get("user.name") } returns MemoryEntity("user.name", "Alice", 1000L)

        val result = executor.execute(
            ToolCall("3", "recall", mapOf("key" to "user.name"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("Alice")
    }

    @Test
    fun `recall missing returns error`() = runTest {
        coEvery { dao.get("nope") } returns null

        val result = executor.execute(
            ToolCall("4", "recall", mapOf("key" to "nope"))
        )

        assertThat(result.success).isFalse()
    }

    @Test
    fun `search_memory returns matching entries`() = runTest {
        coEvery { dao.search("name", 5) } returns listOf(
            MemoryEntity("user.name", "Alice", 1000L),
            MemoryEntity("pet.name", "Rex", 2000L)
        )

        val result = executor.execute(
            ToolCall("5", "search_memory", mapOf("query" to "name"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("Alice")
        assertThat(result.data).contains("Rex")
    }

    @Test
    fun `forget deletes and returns success`() = runTest {
        coEvery { dao.delete("k") } returns 1

        val result = executor.execute(
            ToolCall("6", "forget", mapOf("key" to "k"))
        )

        assertThat(result.success).isTrue()
    }

    @Test
    fun `forget missing key returns error`() = runTest {
        coEvery { dao.delete("k") } returns 0

        val result = executor.execute(
            ToolCall("7", "forget", mapOf("key" to "k"))
        )

        assertThat(result.success).isFalse()
    }
}
