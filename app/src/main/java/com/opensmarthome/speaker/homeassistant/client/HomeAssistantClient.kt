package com.opensmarthome.speaker.homeassistant.client

import com.opensmarthome.speaker.homeassistant.model.Area
import com.opensmarthome.speaker.homeassistant.model.Entity
import com.opensmarthome.speaker.homeassistant.model.ServiceCall
import com.opensmarthome.speaker.homeassistant.model.ServiceCallResult
import kotlinx.coroutines.flow.Flow

interface HomeAssistantClient {
    suspend fun getStates(): List<Entity>
    suspend fun getState(entityId: String): Entity
    suspend fun callService(call: ServiceCall): ServiceCallResult
    suspend fun getAreas(): List<Area>
    fun stateChanges(): Flow<Entity>
    suspend fun isConnected(): Boolean
}
