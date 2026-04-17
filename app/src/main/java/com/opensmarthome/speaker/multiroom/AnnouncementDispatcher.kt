package com.opensmarthome.speaker.multiroom

import com.opensmarthome.speaker.assistant.model.AssistantMessage
import com.opensmarthome.speaker.assistant.session.ConversationHistoryManager
import com.opensmarthome.speaker.voice.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Routes a parsed [AnnouncementEnvelope] to its effect. Today: `tts_broadcast`
 * speaks, `heartbeat` is ack'd, `session_handoff` seeds local conversation
 * history. Unknown types are logged and ignored so receivers tolerate newer
 * senders gracefully.
 *
 * Keeping this headless (no Context) makes the dispatcher unit-testable
 * without Android plumbing; TTS and history are both already abstracted.
 *
 * [historyProvider] is a lambda returning the live [ConversationHistoryManager]
 * so the dispatcher doesn't pin its owner's lifecycle. It's null in tests
 * where history seeding isn't exercised.
 */
class AnnouncementDispatcher(
    private val tts: TextToSpeech,
    private val historyProvider: () -> ConversationHistoryManager? = { null },
    private val announcementState: AnnouncementState? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Handle an incoming envelope. Returns a short tag describing the outcome
     * so the caller can log or update a counter.
     */
    fun dispatch(envelope: AnnouncementEnvelope): DispatchOutcome {
        return when (envelope.type) {
            AnnouncementType.TTS_BROADCAST -> handleTtsBroadcast(envelope)
            AnnouncementType.ANNOUNCEMENT -> handleAnnouncement(envelope)
            AnnouncementType.HEARTBEAT -> DispatchOutcome.AcknowledgedHeartbeat
            AnnouncementType.SESSION_HANDOFF -> handleSessionHandoff(envelope)
            else -> {
                Timber.d("Dispatcher: ignoring unhandled type '${envelope.type}' from ${envelope.from}")
                DispatchOutcome.Unhandled(envelope.type)
            }
        }
    }

    private fun handleTtsBroadcast(envelope: AnnouncementEnvelope): DispatchOutcome {
        val text = envelope.payload["text"] as? String
        if (text.isNullOrBlank()) {
            return DispatchOutcome.Rejected("tts_broadcast missing text")
        }
        scope.launch {
            runCatching { tts.speak(text) }
                .onFailure { Timber.w(it, "TTS speak from announcement failed") }
        }
        return DispatchOutcome.Spoke(text)
    }

    /**
     * Handle an `announcement` envelope — persistent variant of `tts_broadcast`.
     * The receiver both speaks the text (so nobody in the room misses it live)
     * AND pushes it into [AnnouncementState] as a banner on the Ambient screen
     * for `ttl_seconds` so anyone who walked in mid-announcement still gets
     * the message visually.
     *
     * TTL is clamped to [TTL_MIN_SECONDS]..[TTL_MAX_SECONDS] on the receive
     * side — the send side does the same clamp, but we can't trust remote
     * senders to honour the protocol.
     */
    private fun handleAnnouncement(envelope: AnnouncementEnvelope): DispatchOutcome {
        val text = (envelope.payload["text"] as? String)?.takeIf { it.isNotBlank() }
            ?: return DispatchOutcome.Rejected("announcement missing text")
        val rawTtl = (envelope.payload["ttl_seconds"] as? Number)?.toInt()
            ?: return DispatchOutcome.Rejected("announcement missing ttl_seconds")
        val ttl = rawTtl.coerceIn(TTL_MIN_SECONDS, TTL_MAX_SECONDS)
        announcementState?.setAnnouncement(text = text, ttlSeconds = ttl, from = envelope.from)
            ?: Timber.w("announcement received but no AnnouncementState wired (from=${envelope.from})")
        scope.launch {
            runCatching { tts.speak(text) }
                .onFailure { Timber.w(it, "TTS speak from announcement failed") }
        }
        return DispatchOutcome.Announcement(text = text, ttlSeconds = ttl)
    }

    /**
     * Handle session_handoff. For `mode=conversation`, replace the local
     * history with the provided messages (replace, not append — the user
     * said "move this", so the target should pick up where the source
     * left off, not stack on top of its own unrelated session). For
     * `mode=media`, return Unhandled with a TODO; real media transport
     * is deferred (see TODO below).
     */
    private fun handleSessionHandoff(envelope: AnnouncementEnvelope): DispatchOutcome {
        val mode = envelope.payload["mode"] as? String
        return when (mode) {
            AnnouncementBroadcaster.MODE_CONVERSATION -> seedConversation(envelope)
            AnnouncementBroadcaster.MODE_MEDIA -> {
                // TODO(P17.future): media handoff — requires querying the
                // active MediaSession, pausing it locally, and instructing
                // the target to resume from the same playback position.
                // Out of scope for P17.5 (conversation-only).
                Timber.w("session_handoff media not yet wired (from=${envelope.from})")
                DispatchOutcome.Unhandled("session_handoff media not yet wired")
            }
            else -> DispatchOutcome.Rejected("session_handoff missing or unknown mode")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun seedConversation(envelope: AnnouncementEnvelope): DispatchOutcome {
        val rawMessages = envelope.payload["messages"] as? List<*>
            ?: return DispatchOutcome.Rejected("session_handoff missing messages")
        val parsed = rawMessages.mapNotNull { entry ->
            val m = entry as? Map<String, Any?> ?: return@mapNotNull null
            val role = m["role"] as? String ?: return@mapNotNull null
            val content = m["content"] as? String ?: return@mapNotNull null
            when (role) {
                "user" -> AssistantMessage.User(content = content)
                "assistant" -> AssistantMessage.Assistant(content = content)
                "system" -> AssistantMessage.System(content = content)
                else -> null
            }
        }
        val history = historyProvider()
        if (history == null) {
            Timber.w("session_handoff received but no history manager wired (from=${envelope.from})")
            return DispatchOutcome.Rejected("no history manager available")
        }
        // Replace (not append): the handoff moves the conversation — the
        // target shouldn't blend in whatever it was doing before.
        history.clear()
        parsed.forEach { history.add(it) }
        return DispatchOutcome.HandoffSeeded(parsed.size)
    }

    sealed interface DispatchOutcome {
        data class Spoke(val text: String) : DispatchOutcome
        data object AcknowledgedHeartbeat : DispatchOutcome
        data class Unhandled(val type: String) : DispatchOutcome
        data class Rejected(val reason: String) : DispatchOutcome

        /** session_handoff (mode=conversation) seeded [count] messages into local history. */
        data class HandoffSeeded(val count: Int) : DispatchOutcome

        /**
         * `announcement` envelope handled — [text] was pushed to the Ambient
         * banner for [ttlSeconds] AND queued for TTS speak.
         */
        data class Announcement(val text: String, val ttlSeconds: Int) : DispatchOutcome
    }

    companion object {
        /** Minimum TTL (seconds) for an announcement banner. */
        const val TTL_MIN_SECONDS = 5

        /** Maximum TTL (seconds) for an announcement banner — 1 hour. */
        const val TTL_MAX_SECONDS = 3600
    }
}
