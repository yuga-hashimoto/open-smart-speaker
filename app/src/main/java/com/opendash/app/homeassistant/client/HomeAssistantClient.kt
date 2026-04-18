package com.opendash.app.homeassistant.client

import com.opendash.app.homeassistant.model.Area
import com.opendash.app.homeassistant.model.Entity
import com.opendash.app.homeassistant.model.ServiceCall
import com.opendash.app.homeassistant.model.ServiceCallResult
import kotlinx.coroutines.flow.Flow

interface HomeAssistantClient {
    suspend fun getStates(): List<Entity>
    suspend fun getState(entityId: String): Entity
    suspend fun callService(call: ServiceCall): ServiceCallResult
    suspend fun getAreas(): List<Area>
    fun stateChanges(): Flow<Entity>
    suspend fun isConnected(): Boolean
}
