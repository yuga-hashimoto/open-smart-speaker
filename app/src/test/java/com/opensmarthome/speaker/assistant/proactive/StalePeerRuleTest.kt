package com.opensmarthome.speaker.assistant.proactive

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.multiroom.AnnouncementBroadcaster
import com.opensmarthome.speaker.multiroom.PeerFreshness
import com.opensmarthome.speaker.multiroom.PeerLivenessTracker
import com.opensmarthome.speaker.util.DiscoveredSpeaker
import com.opensmarthome.speaker.util.MulticastDiscovery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Tests for [StalePeerRule] — checks the mapping between the tracker's
 * freshness StateFlow and the Suggestion emitted by the rule.
 *
 * The tracker itself is constructed with a StateFlow-backed stub so we
 * never actually start the heartbeat loop.
 */
class StalePeerRuleTest {

    private fun trackerWithStaleness(
        staleness: Map<String, PeerFreshness>
    ): PeerLivenessTracker {
        val discovery = mockk<MulticastDiscovery>(relaxed = true) {
            every { speakers } returns
                MutableStateFlow<List<DiscoveredSpeaker>>(emptyList()).asStateFlow()
        }
        val broadcaster = mockk<AnnouncementBroadcaster>(relaxed = true)
        val tracker = PeerLivenessTracker(
            multicastDiscovery = discovery,
            broadcaster = broadcaster
        )
        // Seed the tracker's public StateFlow via reflection on the private
        // MutableStateFlow. We can't call onHeartbeat because we want
        // deterministic Stale / Gone states without touching the clock.
        val field = PeerLivenessTracker::class.java.getDeclaredField("_staleness")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val mutable = field.get(tracker)
            as MutableStateFlow<Map<String, PeerFreshness>>
        mutable.value = staleness
        return tracker
    }

    @Test
    fun `all peers fresh returns null`() = runTest {
        val tracker = trackerWithStaleness(
            mapOf(
                "Kitchen speaker" to PeerFreshness.Fresh,
                "Bedroom speaker" to PeerFreshness.Fresh
            )
        )
        val rule = StalePeerRule(tracker = tracker, clock = { 0L })

        assertThat(rule.evaluate(now = 0L)).isNull()
    }

    @Test
    fun `one peer gone produces a suggestion naming that peer`() = runTest {
        val tracker = trackerWithStaleness(
            mapOf(
                "Kitchen speaker" to PeerFreshness.Fresh,
                "Bedroom speaker" to PeerFreshness.Gone
            )
        )
        val rule = StalePeerRule(tracker = tracker, clock = { 123L })

        val suggestion = rule.evaluate(now = 123L)

        assertThat(suggestion).isNotNull()
        assertThat(suggestion?.id).isEqualTo("stale_peer_Bedroom speaker")
        assertThat(suggestion?.message).contains("Bedroom speaker")
        assertThat(suggestion?.message).contains("3 minutes")
        assertThat(suggestion?.priority).isEqualTo(Suggestion.Priority.NORMAL)
        assertThat(suggestion?.suggestedAction).isNull()
    }

    @Test
    fun `multiple peers gone picks first alphabetically`() = runTest {
        val tracker = trackerWithStaleness(
            mapOf(
                "Study speaker" to PeerFreshness.Gone,
                "Attic speaker" to PeerFreshness.Gone,
                "Kitchen speaker" to PeerFreshness.Gone
            )
        )
        val rule = StalePeerRule(tracker = tracker, clock = { 0L })

        val suggestion = rule.evaluate(now = 0L)

        assertThat(suggestion?.id).isEqualTo("stale_peer_Attic speaker")
    }

    @Test
    fun `same peer re-evaluated still fires (dedupe is not our job)`() = runTest {
        val tracker = trackerWithStaleness(
            mapOf("Kitchen speaker" to PeerFreshness.Gone)
        )
        val rule = StalePeerRule(tracker = tracker, clock = { 0L })

        val first = rule.evaluate(now = 0L)
        val second = rule.evaluate(now = 0L)

        assertThat(first).isNotNull()
        assertThat(second).isNotNull()
        assertThat(first?.id).isEqualTo(second?.id)
    }

    @Test
    fun `empty staleness map returns null`() = runTest {
        val tracker = trackerWithStaleness(emptyMap())
        val rule = StalePeerRule(tracker = tracker, clock = { 0L })

        assertThat(rule.evaluate(now = 0L)).isNull()
    }

    @Test
    fun `stale-only peers are ignored - only Gone fires`() = runTest {
        val tracker = trackerWithStaleness(
            mapOf(
                "Kitchen speaker" to PeerFreshness.Stale,
                "Bedroom speaker" to PeerFreshness.Stale
            )
        )
        val rule = StalePeerRule(tracker = tracker, clock = { 0L })

        assertThat(rule.evaluate(now = 0L)).isNull()
    }
}
