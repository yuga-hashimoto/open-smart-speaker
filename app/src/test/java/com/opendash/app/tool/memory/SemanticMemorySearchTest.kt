package com.opendash.app.tool.memory

import com.google.common.truth.Truth.assertThat
import com.opendash.app.data.db.MemoryDao
import com.opendash.app.data.db.MemoryEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SemanticMemorySearchTest {

    private class FakeDao(private val rows: List<MemoryEntity>) : MemoryDao {
        override suspend fun upsert(memory: MemoryEntity) = Unit
        override suspend fun get(key: String): MemoryEntity? = rows.find { it.key == key }
        override suspend fun search(query: String, limit: Int) = rows.take(limit)
        override suspend fun list(limit: Int) = rows.take(limit)
        override suspend fun listByPrefix(prefix: String, limit: Int) =
            rows.filter { it.key.startsWith(prefix) }.take(limit)
        override suspend fun delete(key: String): Int = 0
        override suspend fun clear() = Unit
    }

    private fun entry(key: String, value: String) =
        MemoryEntity(key, value, updatedAtMs = 0L)

    @Test
    fun `empty memory returns empty hits`() = runTest {
        val search = SemanticMemorySearch(FakeDao(emptyList()))
        assertThat(search.searchSemantic("anything")).isEmpty()
    }

    @Test
    fun `top hit matches the query topic`() = runTest {
        val rows = listOf(
            entry("favorite_color", "my favorite color is deep blue"),
            entry("favorite_food", "sushi is the best food"),
            entry("pet", "I have a golden retriever named Mocha")
        )
        val search = SemanticMemorySearch(FakeDao(rows))

        val hits = search.searchSemantic("blue color")

        assertThat(hits).isNotEmpty()
        assertThat(hits.first().key).isEqualTo("favorite_color")
    }

    @Test
    fun `respects limit`() = runTest {
        val rows = (1..10).map { entry("color_$it", "color value $it") }
        val search = SemanticMemorySearch(FakeDao(rows))
        val hits = search.searchSemantic("color", limit = 3)
        assertThat(hits.size).isAtMost(3)
    }

    @Test
    fun `query with no matching terms returns empty`() = runTest {
        val rows = listOf(entry("k1", "apple banana"))
        val search = SemanticMemorySearch(FakeDao(rows))
        val hits = search.searchSemantic("zzzzz-nothing-matches")
        assertThat(hits).isEmpty()
    }
}
