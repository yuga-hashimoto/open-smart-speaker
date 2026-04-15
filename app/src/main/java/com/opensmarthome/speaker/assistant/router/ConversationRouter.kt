package com.opensmarthome.speaker.assistant.router

import com.opensmarthome.speaker.assistant.provider.AssistantProvider
import kotlinx.coroutines.flow.StateFlow

interface ConversationRouter {
    val activeProvider: StateFlow<AssistantProvider?>
    val availableProviders: StateFlow<List<AssistantProvider>>
    val policy: StateFlow<RoutingPolicy>

    suspend fun registerProvider(provider: AssistantProvider)
    suspend fun unregisterProvider(providerId: String)
    suspend fun selectProvider(providerId: String)
    suspend fun setPolicy(policy: RoutingPolicy)
    suspend fun resolveProvider(): AssistantProvider
}
