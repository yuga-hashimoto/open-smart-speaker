package com.opendash.app.homeassistant.cache

import com.opendash.app.homeassistant.model.Entity
import kotlinx.coroutines.flow.StateFlow

interface EntityCache {
    val entities: StateFlow<Map<String, Entity>>
    suspend fun refresh()
    fun getByDomain(domain: String): List<Entity>
    fun getById(entityId: String): Entity?
    suspend fun start()
    fun stop()
}
