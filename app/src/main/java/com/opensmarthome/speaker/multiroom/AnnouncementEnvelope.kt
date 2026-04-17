package com.opensmarthome.speaker.multiroom

/**
 * Wire envelope per `docs/multi-room-protocol.md`.
 *
 * Parsed eagerly by [AnnouncementParser]; dispatched by
 * [AnnouncementDispatcher]. Payload stays as a freeform map so unknown
 * message types round-trip as data without schema friction.
 */
data class AnnouncementEnvelope(
    /** Protocol version. Receivers drop on mismatch. */
    val v: Int,
    /** Message kind — see [AnnouncementType]. Unknown types get dropped. */
    val type: String,
    /** Message id; unique per sender. Used for de-duplication (future). */
    val id: String,
    /** Sender service name (mDNS). */
    val from: String,
    /** Unix epoch seconds. Replay window: receivers reject `ts` older than 30 s. */
    val ts: Long,
    /** Payload; schema depends on [type]. */
    val payload: Map<String, Any?>,
    /** base64(hmac_sha256(shared_secret, type|id|ts|payload_json)). */
    val hmac: String
) {

    companion object {
        const val CURRENT_VERSION = 1
        /** Receivers drop envelopes older than this (seconds). */
        const val REPLAY_WINDOW_SECONDS = 30L
    }
}

/** Known message kinds. Unknown types are still parseable as envelopes but not dispatched. */
object AnnouncementType {
    const val TTS_BROADCAST = "tts_broadcast"
    const val TTS_TARGET = "tts_target"
    const val START_TIMER = "start_timer"
    const val CANCEL_TIMER = "cancel_timer"
    const val ANNOUNCEMENT = "announcement"
    const val HEARTBEAT = "heartbeat"
    const val ERROR = "error"

    /**
     * P17.5 — transfer the current conversation (and, eventually, active
     * media) from this speaker to another. Payload:
     *
     *   {
     *     "mode": "conversation" | "media",
     *     "messages": [ {"role": "user"|"assistant"|"system", "content": "..."}, ... ]
     *   }
     *
     * Receivers seed their local history with the provided messages. Media
     * mode is stubbed; real transport will land in a future cycle.
     */
    const val SESSION_HANDOFF = "session_handoff"
}
