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
    private val clock: () -> Long = { System.currentTimeMillis() / 1000L },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
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
        val secret = securePreferences
            .getString(SecurePreferences.KEY_MULTIROOM_SECRET)
            .takeIf { it.isNotBlank() }
            ?: return BroadcastResult(
                sentCount = 0,
                failures = listOf("none" to SendOutcome.Other("no shared secret"))
            )

        val payload: Map<String, Any?> = mapOf(
            "text" to text,
            "language" to language
        )
        val line = buildEnvelopeLine(
            type = AnnouncementType.TTS_BROADCAST,
            payload = payload,
            secret = secret
        )
        return fanOut(line)
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
        return client.send(host = peer.host!!, port = peer.port!!, line = line)
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

    private suspend fun fanOut(line: String): BroadcastResult {
        val peers = discovery.speakers.value.filter {
            !it.host.isNullOrBlank() && it.port != null && it.port > 0
        }
        if (peers.isEmpty()) return BroadcastResult(sentCount = 0, failures = emptyList())

        val results: List<Pair<DiscoveredSpeaker, SendOutcome>> = coroutineScope {
            peers.map { peer ->
                async {
                    peer to client.send(
                        host = peer.host!!,
                        port = peer.port!!,
                        line = line
                    )
                }
            }.awaitAll()
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
