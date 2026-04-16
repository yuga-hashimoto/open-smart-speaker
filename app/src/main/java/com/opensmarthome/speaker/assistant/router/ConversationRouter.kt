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

    /**
     * Resolve the provider to use for the next turn. Optional [userInput] lets
     * the Auto policy consult HeavyTaskDetector and prefer a remote provider
     * when the request is too heavy for the local model.
     */
    suspend fun resolveProvider(userInput: String? = null): AssistantProvider
}
