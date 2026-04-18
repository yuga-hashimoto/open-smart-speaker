package com.opendash.app.tool.memory

import com.opendash.app.data.db.MemoryDao
import com.opendash.app.data.db.MemoryEntity

/**
 * Repository layer over MemoryDao for the Settings UI and other callers
 * that don't want to deal with Room directly.
 */
class MemoryRepository(
    private val dao: MemoryDao
) {

    suspend fun all(limit: Int = 200): List<MemoryEntity> = dao.list(limit)

    suspend fun get(key: String): MemoryEntity? = dao.get(key)

    suspend fun save(key: String, value: String) {
        require(key.isNotBlank()) { "key must not be blank" }
        dao.upsert(MemoryEntity(key, value, System.currentTimeMillis()))
    }

    suspend fun delete(key: String): Boolean = dao.delete(key) > 0

    suspend fun clear() = dao.clear()

    suspend fun search(query: String, limit: Int = 20): List<MemoryEntity> =
        dao.search(query, limit)
}
