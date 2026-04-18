package com.opendash.app.tool.memory

import com.google.common.truth.Truth.assertThat
import com.opendash.app.data.db.MemoryDao
import com.opendash.app.data.db.MemoryEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MemoryRepositoryTest {

    private lateinit var dao: FakeMemoryDao
    private lateinit var repo: MemoryRepository

    @BeforeEach
    fun setup() {
        dao = FakeMemoryDao()
        repo = MemoryRepository(dao)
    }

    @Test
    fun `save then get round-trip`() = runTest {
        repo.save("name", "Alice")
        val row = repo.get("name")
        assertThat(row).isNotNull()
        assertThat(row!!.value).isEqualTo("Alice")
        assertThat(row.updatedAtMs).isGreaterThan(0L)
    }

    @Test
    fun `save rejects blank key`() = runTest {
        assertThrows<IllegalArgumentException> { repo.save("   ", "x") }
    }

    @Test
    fun `save overwrites existing key`() = runTest {
        repo.save("name", "Alice")
        repo.save("name", "Bob")
        assertThat(repo.get("name")?.value).isEqualTo("Bob")
    }

    @Test
    fun `all returns every entry up to limit`() = runTest {
        repo.save("k1", "a")
        repo.save("k2", "b")
        repo.save("k3", "c")
        val rows = repo.all()
        assertThat(rows.map { it.key }).containsExactly("k1", "k2", "k3")
    }

    @Test
    fun `all honors explicit limit`() = runTest {
        repo.save("k1", "a")
        repo.save("k2", "b")
        repo.save("k3", "c")
        assertThat(repo.all(limit = 2)).hasSize(2)
    }

    @Test
    fun `delete removes and returns true`() = runTest {
        repo.save("gone", "x")
        assertThat(repo.delete("gone")).isTrue()
        assertThat(repo.get("gone")).isNull()
    }

    @Test
    fun `delete missing returns false`() = runTest {
        assertThat(repo.delete("nope")).isFalse()
    }

    @Test
    fun `clear empties the store`() = runTest {
        repo.save("a", "1")
        repo.save("b", "2")
        repo.clear()
        assertThat(repo.all()).isEmpty()
    }

    @Test
    fun `search delegates to dao and respects limit`() = runTest {
        repo.save("favorite_color", "blue")
        repo.save("favorite_food", "sushi")
        repo.save("disliked_color", "orange")
        val hits = repo.search("color", limit = 10)
        assertThat(hits.map { it.key }).containsAtLeast("favorite_color", "disliked_color")
    }
}

/** Minimal in-memory MemoryDao implementation for testing. */
private class FakeMemoryDao : MemoryDao {
    private val rows = mutableMapOf<String, MemoryEntity>()

    override suspend fun upsert(memory: MemoryEntity) {
        rows[memory.key] = memory
    }

    override suspend fun get(key: String): MemoryEntity? = rows[key]

    override suspend fun search(query: String, limit: Int): List<MemoryEntity> =
        rows.values.filter { it.key.contains(query) || it.value.contains(query) }.take(limit)

    override suspend fun list(limit: Int): List<MemoryEntity> =
        rows.values.sortedBy { it.key }.take(limit)

    override suspend fun listByPrefix(prefix: String, limit: Int): List<MemoryEntity> =
        rows.values.filter { it.key.startsWith(prefix) }.take(limit)

    override suspend fun delete(key: String): Int =
        if (rows.remove(key) != null) 1 else 0

    override suspend fun clear() {
        rows.clear()
    }
}
