package com.opensmarthome.speaker.multiroom

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.util.DiscoveredSpeaker
import com.opensmarthome.speaker.util.MulticastDiscovery
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for [PeerLivenessTracker]. These cover the pure state-transition contract —
 * how freshness degrades over time — and the periodic broadcast loop.
 *
 * Thresholds under test (must match [PeerLivenessTracker]):
 *  - Heartbeat interval:   30s
 *  - Fresh window:        ≤60s  (2× interval)
 *  - Stale window:       60-180s (up to 6× interval)
 *  - Gone:                >180s or never seen
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PeerLivenessTrackerTest {

    private fun discovery(peers: List<DiscoveredSpeaker>): Pair<MulticastDiscovery, MutableStateFlow<List<DiscoveredSpeaker>>> {
        val d = mockk<MulticastDiscovery>(relaxed = true)
        val flow = MutableStateFlow(peers)
        every { d.speakers } returns flow.asStateFlow()
        return d to flow
    }

    /** A broadcaster that records each heartbeat call count in [counter]. */
    private fun stubBroadcaster(counter: AtomicInteger = AtomicInteger(0)): AnnouncementBroadcaster {
        val b = mockk<AnnouncementBroadcaster>(relaxed = true)
        coEvery { b.broadcastHeartbeat() } answers {
            counter.incrementAndGet()
            BroadcastResult(sentCount = 0, failures = emptyList())
        }
        return b
    }

    private fun envelope(from: String, ts: Long = 0L) = AnnouncementEnvelope(
        v = 1, type = AnnouncementType.HEARTBEAT, id = "id-$from",
        from = from, ts = ts, payload = emptyMap(), hmac = "sig"
    )

    @Test
    fun `peer never seen but present in speakers list reports Gone`() {
        val (d, _) = discovery(listOf(DiscoveredSpeaker("speaker-kitchen", "10.0.0.2", 8421)))
        val tracker = PeerLivenessTracker(
            multicastDiscovery = d,
            broadcaster = stubBroadcaster(),
            clock = { 0L }
        )
        val snapshot = tracker.freshnessSnapshot()
        assertThat(snapshot["speaker-kitchen"]).isEqualTo(PeerFreshness.Gone)
    }

    @Test
    fun `peer seen now reports Fresh`() {
        val now = 1_000L
        val (d, _) = discovery(listOf(DiscoveredSpeaker("speaker-kitchen", "10.0.0.2", 8421)))
        val tracker = PeerLivenessTracker(
            multicastDiscovery = d,
            broadcaster = stubBroadcaster(),
            clock = { now }
        )
        tracker.onHeartbeat(envelope("speaker-kitchen"))
        assertThat(tracker.freshnessSnapshot()["speaker-kitchen"]).isEqualTo(PeerFreshness.Fresh)
    }

    @Test
    fun `peer seen 90 seconds ago reports Stale`() {
        var now = 0L
        val (d, _) = discovery(listOf(DiscoveredSpeaker("speaker-kitchen", "10.0.0.2", 8421)))
        val tracker = PeerLivenessTracker(
            multicastDiscovery = d,
            broadcaster = stubBroadcaster(),
            clock = { now }
        )
        tracker.onHeartbeat(envelope("speaker-kitchen"))
        now = 90_000L
        assertThat(tracker.freshnessSnapshot()["speaker-kitchen"]).isEqualTo(PeerFreshness.Stale)
    }

    @Test
    fun `peer seen 250 seconds ago reports Gone`() {
        var now = 0L
        val (d, _) = discovery(listOf(DiscoveredSpeaker("speaker-kitchen", "10.0.0.2", 8421)))
        val tracker = PeerLivenessTracker(
            multicastDiscovery = d,
            broadcaster = stubBroadcaster(),
            clock = { now }
        )
        tracker.onHeartbeat(envelope("speaker-kitchen"))
        now = 250_000L
        assertThat(tracker.freshnessSnapshot()["speaker-kitchen"]).isEqualTo(PeerFreshness.Gone)
    }

    @Test
    fun `peer at exact 60s boundary reports Fresh`() {
        var now = 0L
        val (d, _) = discovery(listOf(DiscoveredSpeaker("k", "10.0.0.2", 8421)))
        val tracker = PeerLivenessTracker(
            multicastDiscovery = d,
            broadcaster = stubBroadcaster(),
            clock = { now }
        )
        tracker.onHeartbeat(envelope("k"))
        now = 60_000L
        assertThat(tracker.freshnessSnapshot()["k"]).isEqualTo(PeerFreshness.Fresh)
    }

    @Test
    fun `peer at exact 180s boundary reports Stale`() {
        var now = 0L
        val (d, _) = discovery(listOf(DiscoveredSpeaker("k", "10.0.0.2", 8421)))
        val tracker = PeerLivenessTracker(
            multicastDiscovery = d,
            broadcaster = stubBroadcaster(),
            clock = { now }
        )
        tracker.onHeartbeat(envelope("k"))
        now = 180_000L
        assertThat(tracker.freshnessSnapshot()["k"]).isEqualTo(PeerFreshness.Stale)
    }

    @Test
    fun `re-seeing peer resets staleness`() {
        var now = 0L
        val (d, _) = discovery(listOf(DiscoveredSpeaker("k", "10.0.0.2", 8421)))
        val tracker = PeerLivenessTracker(
            multicastDiscovery = d,
            broadcaster = stubBroadcaster(),
            clock = { now }
        )
        tracker.onHeartbeat(envelope("k"))
        now = 120_000L
        assertThat(tracker.freshnessSnapshot()["k"]).isEqualTo(PeerFreshness.Stale)
        tracker.onHeartbeat(envelope("k"))
        assertThat(tracker.freshnessSnapshot()["k"]).isEqualTo(PeerFreshness.Fresh)
    }

    @Test
    fun `peer dropped from speakers list is removed from tracker`() {
        val now = 1_000L
        val peer = DiscoveredSpeaker("k", "10.0.0.2", 8421)
        val (d, flow) = discovery(listOf(peer))
        val tracker = PeerLivenessTracker(
            multicastDiscovery = d,
            broadcaster = stubBroadcaster(),
            clock = { now }
        )
        tracker.onHeartbeat(envelope("k"))
        assertThat(tracker.freshnessSnapshot()).containsKey("k")

        // Simulate MulticastDiscovery dropping the peer (onServiceLost).
        flow.value = emptyList()
        tracker.pruneDroppedPeers()
        assertThat(tracker.freshnessSnapshot()).doesNotContainKey("k")
    }

    @Test
    fun `freshness is reported for every discovered peer even when never seen`() {
        val (d, _) = discovery(
            listOf(
                DiscoveredSpeaker("a", "10.0.0.2", 8421),
                DiscoveredSpeaker("b", "10.0.0.3", 8421)
            )
        )
        val tracker = PeerLivenessTracker(
            multicastDiscovery = d,
            broadcaster = stubBroadcaster(),
            clock = { 1_000L }
        )
        tracker.onHeartbeat(envelope("a"))
        val snap = tracker.freshnessSnapshot()
        assertThat(snap["a"]).isEqualTo(PeerFreshness.Fresh)
        assertThat(snap["b"]).isEqualTo(PeerFreshness.Gone)
    }

    @Test
    fun `start schedules a broadcastHeartbeat every 30 seconds`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val trackerScope = TestScope(dispatcher + SupervisorJob())
        val (d, _) = discovery(emptyList())
        val counter = AtomicInteger(0)
        val broadcaster = stubBroadcaster(counter)
        val tracker = PeerLivenessTracker(
            multicastDiscovery = d,
            broadcaster = broadcaster,
            clock = { scheduler.currentTime },
            scope = trackerScope,
            dispatcher = dispatcher
        )

        tracker.start()
        advanceTimeBy(100)
        assertThat(counter.get()).isEqualTo(1)

        advanceTimeBy(30_000)
        assertThat(counter.get()).isEqualTo(2)

        advanceTimeBy(60_000)
        assertThat(counter.get()).isEqualTo(4)

        tracker.stop()
        // Cancel the tracker's scope explicitly so runTest's final sweep
        // doesn't complain about lingering children.
        trackerScope.cancel()
    }

    @Test
    fun `stop cancels the periodic loop`() = runTest {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val trackerScope = TestScope(dispatcher + SupervisorJob())
        val (d, _) = discovery(emptyList())
        val counter = AtomicInteger(0)
        val broadcaster = stubBroadcaster(counter)
        val tracker = PeerLivenessTracker(
            multicastDiscovery = d,
            broadcaster = broadcaster,
            clock = { scheduler.currentTime },
            scope = trackerScope,
            dispatcher = dispatcher
        )

        tracker.start()
        advanceTimeBy(100)
        assertThat(counter.get()).isEqualTo(1)

        tracker.stop()
        advanceTimeBy(120_000)
        // No further calls after stop.
        assertThat(counter.get()).isEqualTo(1)

        trackerScope.cancel()
    }
}
