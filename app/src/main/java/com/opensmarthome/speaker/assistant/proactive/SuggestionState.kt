package com.opensmarthome.speaker.assistant.proactive

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

/**
 * Publishes proactive suggestions as a Flow that a UI layer can observe.
 * Poll interval is configurable; the engine is only run while the StateFlow
 * has at least one active subscriber to keep it cheap.
 */
class SuggestionState(
    private val engine: SuggestionEngine,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val _current = MutableStateFlow<List<Suggestion>>(emptyList())
    val current: StateFlow<List<Suggestion>> = _current.asStateFlow()

    private val _dismissed = MutableStateFlow<Set<String>>(emptySet())
    val dismissed: StateFlow<Set<String>> = _dismissed.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                try {
                    val all = engine.evaluate()
                    val filtered = all.filter { it.id !in _dismissed.value }
                    _current.value = filtered
                } catch (e: Exception) {
                    Timber.w(e, "Suggestion evaluation failed")
                }
                delay(pollIntervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun dismiss(id: String) {
        _dismissed.value = _dismissed.value + id
        _current.value = _current.value.filter { it.id != id }
    }

    fun clearDismissals() {
        _dismissed.value = emptySet()
    }

    companion object {
        private const val DEFAULT_POLL_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    }
}
