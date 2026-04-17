package com.opensmarthome.speaker.multiroom

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
