package com.opensmarthome.speaker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opensmarthome.speaker.homeassistant.cache.EntityCache
import com.opensmarthome.speaker.homeassistant.client.HomeAssistantClient
import com.opensmarthome.speaker.homeassistant.model.Entity
import com.opensmarthome.speaker.homeassistant.model.ServiceCall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val entityCache: EntityCache,
    private val haClient: HomeAssistantClient
) : ViewModel() {

    val entities: StateFlow<Map<String, Entity>> = entityCache.entities

    private val _groupedEntities = MutableStateFlow<Map<String, List<Entity>>>(emptyMap())
    val groupedEntities: StateFlow<Map<String, List<Entity>>> = _groupedEntities.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    val quickActions = listOf(
        QuickAction("All Lights Off", "light", "turn_off"),
        QuickAction("All Lights On", "light", "turn_on"),
    )

    init {
        viewModelScope.launch {
            entityCache.start()
        }
        viewModelScope.launch {
            entityCache.entities.collect { entityMap ->
                val controllable = entityMap.values.filter { entity ->
                    entity.domain in listOf("light", "switch", "climate", "media_player", "cover", "fan", "input_boolean")
                }
                _groupedEntities.value = controllable.groupBy { it.domain }
            }
        }
        viewModelScope.launch {
            _isConnected.value = haClient.isConnected()
        }
    }

    fun toggleEntity(entity: Entity) {
        viewModelScope.launch {
            try {
                val service = if (entity.state == "on") "turn_off" else "turn_on"
                haClient.callService(
                    ServiceCall(domain = entity.domain, service = service, entityId = entity.entityId)
                )
                entityCache.refresh()
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle ${entity.entityId}")
            }
        }
    }

    fun setBrightness(entity: Entity, brightness: Float) {
        viewModelScope.launch {
            try {
                haClient.callService(
                    ServiceCall(
                        domain = "light",
                        service = "turn_on",
                        entityId = entity.entityId,
                        data = mapOf("brightness" to brightness.toInt())
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to set brightness for ${entity.entityId}")
            }
        }
    }

    fun executeQuickAction(action: QuickAction) {
        viewModelScope.launch {
            try {
                haClient.callService(
                    ServiceCall(
                        domain = action.domain,
                        service = action.service,
                        entityId = action.entityId
                    )
                )
                entityCache.refresh()
            } catch (e: Exception) {
                Timber.e(e, "Failed to execute quick action: ${action.label}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        entityCache.stop()
    }
}
