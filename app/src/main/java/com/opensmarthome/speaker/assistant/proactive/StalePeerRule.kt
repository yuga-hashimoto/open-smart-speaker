package com.opensmarthome.speaker.assistant.proactive

import com.opensmarthome.speaker.multiroom.PeerFreshness
import com.opensmarthome.speaker.multiroom.PeerLivenessTracker

/**
 * Surfaces a proactive suggestion when a multi-room peer speaker has stopped
 * checking in long enough to be considered [PeerFreshness.Gone] by
 * [PeerLivenessTracker] (more than 3 minutes since the last heartbeat — see
 * `PeerLivenessTracker.STALE_WINDOW_MS`).
 *
 * Stale peers are deliberately ignored here: Stale is a borderline "one
 * missed heartbeat" state and firing on it would be noisy. Only the firm
 * Gone transition raises a Suggestion.
 *
 * Dedupe (e.g. not re-surfacing the same peer that is already dismissed or
 * visible) is left to [SuggestionState] — this rule always emits a suggestion
 * with a deterministic id per peer so the state layer can match it.
 *
 * When multiple peers are Gone simultaneously, the first one by service
 * name (alphabetical) is picked for determinism — a single coherent "one
 * speaker at a time" surface is friendlier than a spam of suggestions.
 */
class StalePeerRule(
    private val tracker: PeerLivenessTracker,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : SuggestionRule {

    override suspend fun evaluate(context: ProactiveContext): Suggestion? =
        evaluate(now = clock())

    /**
     * Public overload that matches the task spec: reads
     * `tracker.staleness.value`, finds the first peer whose state is Gone
     * (alphabetically ordered by service name for determinism), and returns
     * a Suggestion for it. Returns null when no peer is Gone.
     */
    fun evaluate(now: Long): Suggestion? {
        val snapshot = tracker.staleness.value
        if (snapshot.isEmpty()) return null

        val firstGone = snapshot
            .asSequence()
            .filter { it.value is PeerFreshness.Gone }
            .map { it.key }
            .sorted()
            .firstOrNull()
            ?: return null

        return Suggestion(
            id = "stale_peer_$firstGone",
            priority = Suggestion.Priority.NORMAL,
            message = "$firstGone hasn't checked in for over 3 minutes. " +
                "Might be off, rebooting, or offline.",
            suggestedAction = null,
            expiresAtMs = now + EXPIRY_WINDOW_MS
        )
    }

    companion object {
        /**
         * How long a single surfaced suggestion stays valid before the
         * state layer is allowed to re-evaluate it. One heartbeat cycle is
         * plenty — if the peer recovers we want the suggestion to clear
         * on the next poll.
         */
        private const val EXPIRY_WINDOW_MS: Long = 30_000L
    }
}
