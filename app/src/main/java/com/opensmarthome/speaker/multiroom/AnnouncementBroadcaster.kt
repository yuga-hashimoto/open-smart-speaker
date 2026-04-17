package com.opensmarthome.speaker.multiroom

import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.data.preferences.SecurePreferences
import com.opensmarthome.speaker.util.DiscoveredSpeaker
import com.opensmarthome.speaker.util.MulticastDiscovery
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fan-out [AnnouncementClient] across every discovered peer (via
 * [MulticastDiscovery]). Builds + signs envelopes and reports per-peer
 * outcomes.
 *
 * Refuses to send without a shared secret — better to fail loudly than
 * to ship signed-but-wrong envelopes that every receiver will drop on
 * HMAC mismatch.
 */
@Singleton
class AnnouncementBroadcaster @Inject constructor(
    private val discovery: MulticastDiscovery,
    private val client: AnnouncementClient,
    private val securePreferences: SecurePreferences,
    moshi: Moshi,
    private val selfServiceName: () -> String?,
    private val groupLookup: suspend (String) -> SpeakerGroup? = { null },
    private val clock: () -> Long = { System.currentTimeMillis() / 1000L },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    /**
     * Optional WebSocket client. When present the broadcaster tries
     * WebSocket transport first and falls back to [client] (NDJSON) only
     * if the handshake fails. Left nullable so legacy call sites and
     * tests can stay on NDJSON without pulling in an [OkHttpClient].
     */
    private val webSocketClient: AnnouncementWebSocketClient? = null,
    /**
     * Optional outbound-traffic counter. When present, each peer-send
     * that resolves to [SendOutcome.Ok] increments the lifetime counter
     * for the envelope's type. Failed sends (timeout, refused, other)
     * deliberately don't count — a flapping network shouldn't inflate
     * the "sent" number the user sees in System Info.
     */
    private val trafficRecorder: MultiroomTrafficRecorder? = null,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {

    private val mapAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    /**
     * Broadcast a `tts_broadcast` envelope to every resolved peer.
     * Returns per-peer outcomes; callers can surface failures in UI.
     */
    suspend fun broadcastTts(
        text: String,
        language: String = "en"
    ): BroadcastResult {
        val secret = requireSecret()
            ?: return BroadcastResult(
                sentCount = 0,
                failures = listOf("none" to SendOutcome.Other("no shared secret"))
            )

        val line = buildTtsLine(text, language, secret)
        return fanOut(line, filter = null, type = AnnouncementType.TTS_BROADCAST)
    }

    /**
     * Broadcast a `tts_broadcast` envelope to a **named group** —
     * client-side persistent subset of the mDNS peer list.
     *
     * Per ADR (docs/multi-room-protocol.md §Group semantics), the group
     * concept never reaches the wire: we resolve the group locally, filter
     * the discovered peer list down to members, and fan out only to that
     * subset. Peers in the group whose mDNS service hasn't been discovered
     * yet are silently skipped (i.e. not counted as failures) — the
     * broadcaster can't contact what it can't resolve, and the group is
     * by definition a best-effort targeting hint.
     *
     * If [groupName] doesn't exist in the repository, returns a single
     * `unknown group` failure so the caller (tool / UI) can surface a
     * "no such group" message instead of silently sending to nobody.
     */
    suspend fun broadcastTtsToGroup(
        groupName: String,
        text: String,
        language: String = "en"
    ): BroadcastResult {
        val group = groupLookup(groupName)
            ?: return BroadcastResult(
                sentCount = 0,
                failures = listOf("missing" to SendOutcome.Other("unknown group: $groupName"))
            )
        val secret = requireSecret()
            ?: return BroadcastResult(
                sentCount = 0,
                failures = listOf("none" to SendOutcome.Other("no shared secret"))
            )

        val line = buildTtsLine(text, language, secret)
        val allowed = group.memberServiceNames
        return fanOut(
            line,
            filter = { peer -> peer.serviceName in allowed },
            type = AnnouncementType.TTS_BROADCAST
        )
    }

    private fun requireSecret(): String? =
        securePreferences.getString(SecurePreferences.KEY_MULTIROOM_SECRET)
            .takeIf { it.isNotBlank() }

    /**
     * Broadcast a `heartbeat` envelope to every resolved peer. Payload is
     * empty by design — the envelope's purpose is liveness signalling, not
     * data transport. Receivers update their per-peer `lastSeenMs` via
     * [PeerLivenessTracker.onHeartbeat] and ack the envelope through
     * [AnnouncementDispatcher].
     *
     * Missing shared secret short-circuits (same policy as every other
     * broadcaster call): we'd rather emit nothing than send envelopes every
     * peer will drop on HMAC mismatch.
     */
    suspend fun broadcastHeartbeat(): BroadcastResult {
        val secret = requireSecret()
            ?: return BroadcastResult(
                sentCount = 0,
                failures = listOf("none" to SendOutcome.Other("no shared secret"))
            )
        val line = buildEnvelopeLine(
            type = AnnouncementType.HEARTBEAT,
            payload = emptyMap(),
            secret = secret
        )
        return fanOut(line, filter = null, type = AnnouncementType.HEARTBEAT)
    }

    /**
     * Broadcast a `start_timer` envelope to every resolved peer. Cross-speaker
     * timer sync — saying "set a 5-minute timer on every speaker" fires the
     * same `set_timer` effect on each peer so all devices alert in unison.
     *
     * Seconds must be > 0. Label is optional; when present it tags the timer
     * consistently across peers.
     */
    suspend fun broadcastTimer(
        seconds: Int,
        label: String? = null
    ): BroadcastResult {
        if (seconds <= 0) {
            return BroadcastResult(
                sentCount = 0,
                failures = listOf("invalid" to SendOutcome.Other("timer seconds must be positive"))
            )
        }
        val secret = requireSecret()
            ?: return BroadcastResult(
                sentCount = 0,
                failures = listOf("none" to SendOutcome.Other("no shared secret"))
            )

        val payload: Map<String, Any?> = mapOf(
            "seconds" to seconds,
            "label" to label
        )
        val line = buildEnvelopeLine(
            type = AnnouncementType.START_TIMER,
            payload = payload,
            secret = secret
        )
        return fanOut(line, filter = null, type = AnnouncementType.START_TIMER)
    }

    /**
     * Broadcast a `cancel_timer` envelope to every resolved peer —
     * complement of [broadcastTimer]. Each receiver's
     * [AnnouncementDispatcher] calls its local [TimerManager.cancelAllTimers]
     * (when [id] is null or "all") or [TimerManager.cancelTimer] (when [id]
     * is a specific timer id).
     *
     * The wire payload always carries an `id` field — the sentinel string
     * `"all"` when the caller passed null — so receivers can disambiguate
     * "cancel everything" from a genuinely-absent field (which they'd
     * otherwise be forced to interpret, risking divergent behaviour per
     * peer).
     */
    suspend fun broadcastCancelTimer(id: String? = null): BroadcastResult {
        val secret = requireSecret()
            ?: return BroadcastResult(
                sentCount = 0,
                failures = listOf("none" to SendOutcome.Other("no shared secret"))
            )

        val payload: Map<String, Any?> = mapOf(
            "id" to (id ?: CANCEL_TIMER_ID_ALL)
        )
        val line = buildEnvelopeLine(
            type = AnnouncementType.CANCEL_TIMER,
            payload = payload,
            secret = secret
        )
        return fanOut(line, filter = null, type = AnnouncementType.CANCEL_TIMER)
    }

    private fun buildTtsLine(text: String, language: String, secret: String): String {
        val payload: Map<String, Any?> = mapOf(
            "text" to text,
            "language" to language
        )
        return buildEnvelopeLine(
            type = AnnouncementType.TTS_BROADCAST,
            payload = payload,
            secret = secret
        )
    }

    /**
     * Broadcast a persistent `announcement` envelope to every resolved peer.
     *
     * Receivers speak the text AND surface it as a banner on their Ambient
     * screen for [ttlSeconds]. See [AnnouncementType.ANNOUNCEMENT] and
     * [AnnouncementDispatcher.handleAnnouncement].
     *
     * [ttlSeconds] is clamped to
     * [AnnouncementDispatcher.TTL_MIN_SECONDS]..[AnnouncementDispatcher.TTL_MAX_SECONDS];
     * out-of-range values are quietly coerced rather than rejected so a caller
     * passing `0` still gets the minimum sensible banner instead of a silent
     * no-op.
     */
    suspend fun broadcastAnnouncement(
        text: String,
        ttlSeconds: Int = DEFAULT_ANNOUNCEMENT_TTL_SECONDS
    ): BroadcastResult {
        val secret = requireSecret()
            ?: return BroadcastResult(
                sentCount = 0,
                failures = listOf("none" to SendOutcome.Other("no shared secret"))
            )
        val clampedTtl = ttlSeconds.coerceIn(
            AnnouncementDispatcher.TTL_MIN_SECONDS,
            AnnouncementDispatcher.TTL_MAX_SECONDS
        )
        val payload: Map<String, Any?> = mapOf(
            "text" to text,
            "ttl_seconds" to clampedTtl
        )
        val line = buildEnvelopeLine(
            type = AnnouncementType.ANNOUNCEMENT,
            payload = payload,
            secret = secret
        )
        return fanOut(line, filter = null, type = AnnouncementType.ANNOUNCEMENT)
    }

    /**
     * Send a `session_handoff` envelope for mode=conversation to exactly
     * one peer. [targetServiceName] is matched against discovered peers'
     * mDNS `serviceName` using a case-insensitive prefix/substring match,
     * so either a full name ("speaker-kitchen") or a friendly alias
     * ("kitchen") works.
     *
     * Returns the [SendOutcome] from the single send, or a failure outcome
     * when the target can't be resolved or the secret is missing.
     */
    suspend fun handoffConversation(
        targetServiceName: String,
        messages: List<AssistantMessage>
    ): SendOutcome {
        val secret = securePreferences
            .getString(SecurePreferences.KEY_MULTIROOM_SECRET)
            .takeIf { it.isNotBlank() }
            ?: return SendOutcome.Other("no shared secret")

        val peer = resolvePeer(targetServiceName)
            ?: return SendOutcome.Other("no peer matching '$targetServiceName'")

        val payload: Map<String, Any?> = mapOf(
            "mode" to MODE_CONVERSATION,
            "messages" to messages.mapNotNull { it.toWirePair() }
        )
        val line = buildEnvelopeLine(
            type = AnnouncementType.SESSION_HANDOFF,
            payload = payload,
            secret = secret
        )
        return sendWithFallback(host = peer.host!!, port = peer.port!!, line = line)
    }

    /**
     * Try WebSocket transport first (primary per ADR); fall back to NDJSON
     * on any non-OK outcome so a peer that only speaks the legacy protocol
     * still receives the envelope. We treat *any* non-[SendOutcome.Ok]
     * result from the WS client as reason to retry on NDJSON — a false
     * positive "already delivered" is far worse than a duplicate NDJSON
     * attempt that will just fail fast too.
     *
     * When [webSocketClient] is null, we go straight to NDJSON (backward-
     * compatible code path used by tests and by the deployed v17.2 binary).
     */
    private suspend fun sendWithFallback(
        host: String,
        port: Int,
        line: String
    ): SendOutcome {
        val ws = webSocketClient ?: return client.send(host = host, port = port, line = line)
        val wsOutcome = ws.send(host = host, port = port, line = line)
        return if (wsOutcome is SendOutcome.Ok) wsOutcome
        else client.send(host = host, port = port, line = line)
    }

    /**
     * Resolve [target] to a fully-routable [DiscoveredSpeaker]. Match order:
     * exact serviceName, case-insensitive equality, then case-insensitive
     * substring (so "kitchen" hits "speaker-kitchen"). Only peers with a
     * non-null host + port are considered.
     */
    private fun resolvePeer(target: String): DiscoveredSpeaker? {
        val routable = discovery.speakers.value.filter {
            !it.host.isNullOrBlank() && it.port != null && it.port > 0
        }
        if (routable.isEmpty()) return null
        val trimmed = target.trim()
        if (trimmed.isEmpty()) return null
        routable.firstOrNull { it.serviceName == trimmed }?.let { return it }
        val lower = trimmed.lowercase()
        routable.firstOrNull { it.serviceName.equals(trimmed, ignoreCase = true) }?.let { return it }
        return routable.firstOrNull { it.serviceName.lowercase().contains(lower) }
    }

    private fun AssistantMessage.toWirePair(): Map<String, Any?>? = when (this) {
        is AssistantMessage.User -> mapOf("role" to "user", "content" to content)
        is AssistantMessage.Assistant -> mapOf("role" to "assistant", "content" to content)
        is AssistantMessage.System -> mapOf("role" to "system", "content" to content)
        // Tool results and deltas aren't portable across a fresh session on
        // the target — skip them to keep the handoff payload sane.
        is AssistantMessage.ToolCallResult -> null
        is AssistantMessage.Delta -> null
    }

    private fun buildEnvelopeLine(
        type: String,
        payload: Map<String, Any?>,
        secret: String
    ): String {
        val id = idGenerator()
        val ts = clock()
        val from = selfServiceName() ?: DEFAULT_FROM
        val payloadJson = mapAdapter.toJson(payload)
        val hmac = HmacSigner.sign(secret, type, id, ts, payloadJson)
        val envelope: Map<String, Any?> = mapOf(
            "v" to AnnouncementEnvelope.CURRENT_VERSION,
            "type" to type,
            "id" to id,
            "from" to from,
            "ts" to ts,
            "payload" to payload,
            "hmac" to hmac
        )
        return mapAdapter.toJson(envelope)
    }

    private suspend fun fanOut(
        line: String,
        filter: ((DiscoveredSpeaker) -> Boolean)?,
        type: String
    ): BroadcastResult {
        val resolved = discovery.speakers.value.filter {
            !it.host.isNullOrBlank() && it.port != null && it.port > 0
        }
        val peers = if (filter == null) resolved else resolved.filter(filter)
        if (peers.isEmpty()) return BroadcastResult(sentCount = 0, failures = emptyList())

        val results: List<Pair<DiscoveredSpeaker, SendOutcome>> = coroutineScope {
            peers.map { peer ->
                async {
                    peer to sendWithFallback(
                        host = peer.host!!,
                        port = peer.port!!,
                        line = line
                    )
                }
            }.awaitAll()
        }
        // Count one lifetime outbound per successful peer-send — failures
        // (timeout / refused / other) deliberately don't increment, so a
        // flapping network can't lie about how many envelopes actually
        // reached the wire.
        val recorder = trafficRecorder
        if (recorder != null) {
            val ts = nowMs()
            results.forEach { (_, outcome) ->
                if (outcome is SendOutcome.Ok) recorder.recordOutbound(type = type, nowMs = ts)
            }
        }
        val sent = results.count { it.second is SendOutcome.Ok }
        val failures = results
            .filter { it.second !is SendOutcome.Ok }
            .map { it.first.serviceName to it.second }
        return BroadcastResult(sentCount = sent, failures = failures)
    }

    companion object {
        /** Fallback `from` value when mDNS registration hasn't happened yet. */
        const val DEFAULT_FROM = "speaker"

        /** Value for session_handoff `payload.mode` when transferring conversation history. */
        const val MODE_CONVERSATION = "conversation"

        /** Value for session_handoff `payload.mode` when transferring active media (stub). */
        const val MODE_MEDIA = "media"

        /** Default TTL (seconds) for a persistent announcement banner. */
        const val DEFAULT_ANNOUNCEMENT_TTL_SECONDS = 60

        /**
         * Sentinel payload id for a multi-room timer cancel that targets
         * every active timer. See [broadcastCancelTimer] and
         * [AnnouncementDispatcher.handleCancelTimer].
         */
        const val CANCEL_TIMER_ID_ALL = "all"
    }
}

/**
 * Aggregate result of a broadcast fan-out.
 *
 * @property sentCount number of peers that received the envelope without
 *   error (i.e. reported [SendOutcome.Ok]).
 * @property failures per-peer failures, keyed by mDNS service name.
 */
data class BroadcastResult(
    val sentCount: Int,
    val failures: List<Pair<String, SendOutcome>>
)
