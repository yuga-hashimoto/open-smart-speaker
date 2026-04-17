package com.opensmarthome.speaker.multiroom

import com.opensmarthome.speaker.util.MulticastDiscovery
import kotlinx.coroutines.CoroutineDispatcher
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Freshness classification for a single peer. Derived from the time elapsed
 * since the tracker last received a `heartbeat` envelope from the peer.
 *
 * Thresholds (see [PeerLivenessTracker]):
 *  - [Fresh]: last seen within 60s (2× heartbeat interval)
 *  - [Stale]: last seen within 180s (6× interval)
 *  - [Gone]:  last seen longer than 180s ago, or never seen at all
 */
sealed interface PeerFreshness {
    data object Fresh : PeerFreshness
    data object Stale : PeerFreshness
    data object Gone : PeerFreshness
}

/**
 * Tracks per-peer liveness for multi-room speakers.
 *
 * **Protocol:** every [HEARTBEAT_INTERVAL_MS] the tracker asks the
 * [AnnouncementBroadcaster] to fan out a `heartbeat` envelope to every
 * discovered peer. Peers that run this code do the same, so each device
 * hears heartbeats from its siblings on a steady cadence. Incoming
 * heartbeats bump the peer's `lastSeenMs` via [onHeartbeat]. Freshness
 * is classified against two thresholds:
 *
 *  | elapsed since last seen | state  |
 *  |-------------------------|--------|
 *  | ≤ 60s (2× interval)     | Fresh  |
 *  | ≤ 180s (6× interval)    | Stale  |
 *  | > 180s or never seen    | Gone   |
 *
 * Peers that drop out of [MulticastDiscovery.speakers] (onServiceLost)
 * must be pruned explicitly via [pruneDroppedPeers] — otherwise the
 * "gone" list grows forever as devices come and go.
 *
 * The tracker is wired through DI: [AnnouncementDispatcher] forwards
 * every `heartbeat` dispatch to [onHeartbeat], and the [VoiceService]
 * drives [start] / [stop] when multi-room is enabled.
 */
@Singleton
class PeerLivenessTracker internal constructor(
    private val multicastDiscovery: MulticastDiscovery,
    private val broadcaster: AnnouncementBroadcaster,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    /**
     * Hilt entry point — wires production defaults for clock / scope / dispatcher.
     * Tests should use the internal constructor above to inject virtual time.
     */
    @Inject
    constructor(
        multicastDiscovery: MulticastDiscovery,
        broadcaster: AnnouncementBroadcaster
    ) : this(
        multicastDiscovery = multicastDiscovery,
        broadcaster = broadcaster,
        clock = { System.currentTimeMillis() },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        dispatcher = Dispatchers.Default
    )


    /**
     * Last-seen epoch-ms per peer, keyed by mDNS serviceName. Access is
     * synchronised on `this` because [onHeartbeat] may fire from the
     * dispatcher's inbound thread while [start] runs on its own scope.
     */
    private val lastSeen: MutableMap<String, Long> = linkedMapOf()

    private val _staleness = MutableStateFlow<Map<String, PeerFreshness>>(emptyMap())
    /**
     * Observable freshness map — emits a new snapshot every time the tracker
     * observes a heartbeat, prunes a lost peer, or recomputes on its own
     * periodic loop. UI (e.g. SystemInfoScreen) collects this and surfaces
     * each peer's suffix.
     */
    val staleness: StateFlow<Map<String, PeerFreshness>> = _staleness.asStateFlow()

    private var loopJob: Job? = null

    /**
     * Start the periodic heartbeat loop + freshness recompute. Idempotent —
     * calling twice without [stop] is a no-op so VoiceService.onCreate
     * doesn't need to track whether we're already running.
     */
    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch(dispatcher) {
            while (isActive) {
                runCatching { broadcaster.broadcastHeartbeat() }
                    .onFailure { Timber.w(it, "broadcastHeartbeat failed") }
                pruneDroppedPeers()
                recomputeStaleness()
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    /** Tear down the periodic loop. Safe to call when not started. */
    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    /**
     * Record an incoming heartbeat. Called from [AnnouncementDispatcher]
     * every time a `heartbeat` envelope is dispatched.
     */
    fun onHeartbeat(envelope: AnnouncementEnvelope) {
        val from = envelope.from.trim()
        if (from.isEmpty()) return
        synchronized(this) { lastSeen[from] = clock() }
        recomputeStaleness()
    }

    /**
     * Remove tracker entries for peers no longer in [MulticastDiscovery.speakers].
     * Called periodically by the loop and eagerly from tests.
     */
    fun pruneDroppedPeers() {
        val currentNames = multicastDiscovery.speakers.value
            .map { it.serviceName }
            .toSet()
        val changed = synchronized(this) {
            val before = lastSeen.keys.toSet()
            lastSeen.keys.retainAll(currentNames)
            before != lastSeen.keys
        }
        if (changed) recomputeStaleness()
    }

    /**
     * Return the current freshness map. Includes every peer currently in
     * [MulticastDiscovery.speakers] (so callers can render a suffix for each
     * one) — peers that have never been seen are reported as [PeerFreshness.Gone].
     */
    fun freshnessSnapshot(): Map<String, PeerFreshness> {
        val now = clock()
        val seen = synchronized(this) { lastSeen.toMap() }
        val peers = multicastDiscovery.speakers.value.map { it.serviceName }
        val known = seen.keys + peers
        return known.associateWith { name ->
            val last = seen[name] ?: return@associateWith PeerFreshness.Gone
            classify(now - last)
        }
    }

    private fun recomputeStaleness() {
        _staleness.value = freshnessSnapshot()
    }

    private fun classify(elapsedMs: Long): PeerFreshness = when {
        elapsedMs <= FRESH_WINDOW_MS -> PeerFreshness.Fresh
        elapsedMs <= STALE_WINDOW_MS -> PeerFreshness.Stale
        else -> PeerFreshness.Gone
    }

    companion object {
        /** How often the tracker broadcasts a heartbeat. */
        const val HEARTBEAT_INTERVAL_MS: Long = 30_000L

        /** Last-seen window classified as [PeerFreshness.Fresh]. 2× interval. */
        const val FRESH_WINDOW_MS: Long = 60_000L

        /** Last-seen window classified as [PeerFreshness.Stale]. 6× interval. */
        const val STALE_WINDOW_MS: Long = 180_000L
    }
}
