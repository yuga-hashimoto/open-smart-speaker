package com.opensmarthome.speaker.multiroom

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import timber.log.Timber

/**
 * JSON → AnnouncementEnvelope with layered validation:
 * 1. Shape + required fields (reject fast if missing).
 * 2. Protocol version (reject if != CURRENT_VERSION).
 * 3. Replay window (reject if `ts` older than REPLAY_WINDOW_SECONDS from `now`).
 * 4. HMAC (reject if mismatch).
 *
 * Separated from the socket reader so unit tests stay pure-JVM.
 */
class AnnouncementParser(
    private val moshi: Moshi,
    private val sharedSecretProvider: () -> String?,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000L }
) {

    sealed interface ParseResult {
        data class Ok(val envelope: AnnouncementEnvelope) : ParseResult
        data class Rejected(val reason: Reason, val detail: String = "") : ParseResult
    }

    enum class Reason {
        MALFORMED_JSON,
        MISSING_FIELD,
        VERSION_MISMATCH,
        REPLAY_WINDOW,
        NO_SECRET,
        HMAC_MISMATCH
    }

    fun parse(line: String): ParseResult {
        val rawMap = try {
            @Suppress("UNCHECKED_CAST")
            mapAdapter.fromJson(line) as? Map<String, Any?>
        } catch (e: JsonDataException) {
            return ParseResult.Rejected(Reason.MALFORMED_JSON, e.message ?: "")
        } catch (e: Exception) {
            return ParseResult.Rejected(Reason.MALFORMED_JSON, e.message ?: "")
        } ?: return ParseResult.Rejected(Reason.MALFORMED_JSON)

        val v = (rawMap["v"] as? Number)?.toInt()
            ?: return ParseResult.Rejected(Reason.MISSING_FIELD, "v")
        if (v != AnnouncementEnvelope.CURRENT_VERSION) {
            return ParseResult.Rejected(Reason.VERSION_MISMATCH, "got=$v")
        }
        val type = rawMap["type"] as? String
            ?: return ParseResult.Rejected(Reason.MISSING_FIELD, "type")
        val id = rawMap["id"] as? String
            ?: return ParseResult.Rejected(Reason.MISSING_FIELD, "id")
        val from = rawMap["from"] as? String
            ?: return ParseResult.Rejected(Reason.MISSING_FIELD, "from")
        val ts = (rawMap["ts"] as? Number)?.toLong()
            ?: return ParseResult.Rejected(Reason.MISSING_FIELD, "ts")
        @Suppress("UNCHECKED_CAST")
        val payload = rawMap["payload"] as? Map<String, Any?>
            ?: return ParseResult.Rejected(Reason.MISSING_FIELD, "payload")
        val hmac = rawMap["hmac"] as? String
            ?: return ParseResult.Rejected(Reason.MISSING_FIELD, "hmac")

        val nowSec = clock()
        val ageSec = nowSec - ts
        if (ageSec > AnnouncementEnvelope.REPLAY_WINDOW_SECONDS || ageSec < -AnnouncementEnvelope.REPLAY_WINDOW_SECONDS) {
            return ParseResult.Rejected(Reason.REPLAY_WINDOW, "age=${ageSec}s")
        }

        val secret = sharedSecretProvider()
        if (secret.isNullOrBlank()) {
            return ParseResult.Rejected(Reason.NO_SECRET)
        }
        val payloadJson = mapAdapter.toJson(payload)
        if (!HmacSigner.verify(secret, type, id, ts, payloadJson, hmac)) {
            Timber.d("Envelope HMAC mismatch: type=$type id=$id")
            return ParseResult.Rejected(Reason.HMAC_MISMATCH)
        }

        return ParseResult.Ok(
            AnnouncementEnvelope(
                v = v,
                type = type,
                id = id,
                from = from,
                ts = ts,
                payload = payload,
                hmac = hmac
            )
        )
    }

    private val mapAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )
}
