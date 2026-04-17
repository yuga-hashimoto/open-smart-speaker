package com.opensmarthome.speaker.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Freshness bucket for a multi-room peer. Devices send a periodic heartbeat
 * on the mesh; [Fresh] means one arrived recently, [Stale] means the last one
 * is older than the stale threshold but the peer hasn't been declared dead,
 * and [Gone] means nothing has been heard for long enough to consider the
 * peer unreachable.
 */
enum class Staleness { Fresh, Stale, Gone }

/**
 * Tracks how recently each multi-room peer was heard from. The actual
 * heartbeat pump belongs in a follow-up PR — this component is the
 * observable sink so the UI can already surface mesh health, and tests can
 * stub the map directly via [update].
 */
@Singleton
class PeerLivenessTracker @Inject constructor() {

    private val _staleness = MutableStateFlow<Map<String, Staleness>>(emptyMap())

    /** Per-peer freshness keyed by mDNS service name. */
    val staleness: StateFlow<Map<String, Staleness>> = _staleness.asStateFlow()

    /** Replace the current per-peer freshness map. */
    fun update(next: Map<String, Staleness>) {
        _staleness.value = next.toMap()
    }

    /** Record a single peer's current freshness. */
    fun record(serviceName: String, state: Staleness) {
        _staleness.value = _staleness.value.toMutableMap().apply {
            put(serviceName, state)
        }
    }

    /** Drop a peer entirely (e.g. the service was lost by mDNS). */
    fun remove(serviceName: String) {
        _staleness.value = _staleness.value.toMutableMap().apply {
            remove(serviceName)
        }
    }
}
