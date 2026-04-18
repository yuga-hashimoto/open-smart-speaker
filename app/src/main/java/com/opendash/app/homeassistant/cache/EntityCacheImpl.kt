package com.opendash.app.homeassistant.cache

import com.opendash.app.homeassistant.client.HomeAssistantClient
import com.opendash.app.homeassistant.client.HomeAssistantConfig
import com.opendash.app.homeassistant.model.Entity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class EntityCacheImpl(
    private val haClient: HomeAssistantClient,
    private val config: HomeAssistantConfig
) : EntityCache {

    private val _entities = MutableStateFlow<Map<String, Entity>>(emptyMap())
    override val entities: StateFlow<Map<String, Entity>> = _entities.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null

    override suspend fun refresh() {
        try {
            val states = haClient.getStates()
            _entities.value = states.associateBy { it.entityId }
            Timber.d("Entity cache refreshed: ${states.size} entities")
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh entity cache")
        }
    }

    override fun getByDomain(domain: String): List<Entity> =
        _entities.value.values.filter { it.domain == domain }

    override fun getById(entityId: String): Entity? =
        _entities.value[entityId]

    override suspend fun start() {
        refresh()
        refreshJob = scope.launch {
            while (isActive) {
                delay(config.refreshIntervalMs)
                refresh()
            }
        }
    }

    override fun stop() {
        refreshJob?.cancel()
        refreshJob = null
    }
}
