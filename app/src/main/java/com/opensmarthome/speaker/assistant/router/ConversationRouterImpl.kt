package com.opensmarthome.speaker.assistant.router

import com.opensmarthome.speaker.assistant.provider.AssistantProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRouterImpl @Inject constructor() : ConversationRouter {

    private val _availableProviders = MutableStateFlow<List<AssistantProvider>>(emptyList())
    override val availableProviders: StateFlow<List<AssistantProvider>> = _availableProviders.asStateFlow()

    private val _activeProvider = MutableStateFlow<AssistantProvider?>(null)
    override val activeProvider: StateFlow<AssistantProvider?> = _activeProvider.asStateFlow()

    private val _policy = MutableStateFlow<RoutingPolicy>(RoutingPolicy.Auto)
    override val policy: StateFlow<RoutingPolicy> = _policy.asStateFlow()

    override suspend fun registerProvider(provider: AssistantProvider) {
        val updated = _availableProviders.value.filter { it.id != provider.id } + provider
        _availableProviders.value = updated
        Timber.d("Registered provider: ${provider.id}")
    }

    override suspend fun unregisterProvider(providerId: String) {
        _availableProviders.value = _availableProviders.value.filter { it.id != providerId }
        if (_activeProvider.value?.id == providerId) {
            _activeProvider.value = null
        }
        Timber.d("Unregistered provider: $providerId")
    }

    override suspend fun selectProvider(providerId: String) {
        val provider = _availableProviders.value.find { it.id == providerId }
            ?: throw IllegalArgumentException("Provider not found: $providerId")
        _activeProvider.value = provider
        _policy.value = RoutingPolicy.Manual(providerId)
    }

    override suspend fun setPolicy(policy: RoutingPolicy) {
        _policy.value = policy
    }

    override suspend fun resolveProvider(userInput: String?): AssistantProvider {
        return when (val currentPolicy = _policy.value) {
            is RoutingPolicy.Manual -> {
                _availableProviders.value.find { it.id == currentPolicy.providerId }
                    ?.takeIf { runCatching { it.isAvailable() }.getOrDefault(false) }
                    ?: throw NoAvailableProviderException("Manual provider ${currentPolicy.providerId} unavailable")
            }
            is RoutingPolicy.Auto -> resolveAuto(userInput)
            is RoutingPolicy.Failover -> resolveFailover(currentPolicy.ordered)
            is RoutingPolicy.LowestLatency -> resolveLowestLatency()
        }.also { _activeProvider.value = it }
    }

    private suspend fun resolveAuto(userInput: String?): AssistantProvider {
        val available = _availableProviders.value.filter { provider ->
            runCatching { provider.isAvailable() }.getOrDefault(false)
        }
        if (available.isEmpty()) throw NoAvailableProviderException("No available providers")

        // Prefer a remote provider when HeavyTaskDetector decides the local
        // model isn't a good fit (long input, heavy keywords, vision gap).
        if (userInput != null) {
            val local = available.firstOrNull { it.capabilities.isLocal }
            if (local != null) {
                val decision = HeavyTaskDetector.decide(userInput, local.capabilities)
                if (decision.escalate) {
                    val remote = available.firstOrNull { !it.capabilities.isLocal }
                    if (remote != null) {
                        Timber.d("Escalating to ${remote.id}: ${decision.reason}")
                        return remote
                    }
                }
            }
        }
        return available.first()
    }

    private suspend fun resolveFailover(ordered: List<String>): AssistantProvider {
        for (providerId in ordered) {
            val provider = _availableProviders.value.find { it.id == providerId } ?: continue
            if (runCatching { provider.isAvailable() }.getOrDefault(false)) {
                return provider
            }
        }
        throw NoAvailableProviderException("All failover providers unavailable")
    }

    private suspend fun resolveLowestLatency(): AssistantProvider {
        return _availableProviders.value
            .filter { runCatching { it.isAvailable() }.getOrDefault(false) }
            .minByOrNull { runCatching { it.latencyMs() }.getOrDefault(Long.MAX_VALUE) }
            ?: throw NoAvailableProviderException("No available providers for latency check")
    }
}

class NoAvailableProviderException(message: String) : Exception(message)
