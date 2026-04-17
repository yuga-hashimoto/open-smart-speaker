package com.opensmarthome.speaker.multiroom

import com.opensmarthome.speaker.voice.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes a parsed [AnnouncementEnvelope] to its effect. Only `tts_broadcast`
 * is wired today — other types are logged and ignored so receivers tolerate
 * newer senders gracefully.
 *
 * Keeping this headless (no Context) makes the dispatcher unit-testable
 * without Android plumbing; the TextToSpeech is already abstracted.
 */
@Singleton
class AnnouncementDispatcher @Inject constructor(
    private val tts: TextToSpeech
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Handle an incoming envelope. Returns a short tag describing the outcome
     * so the caller can log or update a counter.
     */
    fun dispatch(envelope: AnnouncementEnvelope): DispatchOutcome {
        return when (envelope.type) {
            AnnouncementType.TTS_BROADCAST -> handleTtsBroadcast(envelope)
            AnnouncementType.HEARTBEAT -> DispatchOutcome.AcknowledgedHeartbeat
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

    sealed interface DispatchOutcome {
        data class Spoke(val text: String) : DispatchOutcome
        data object AcknowledgedHeartbeat : DispatchOutcome
        data class Unhandled(val type: String) : DispatchOutcome
        data class Rejected(val reason: String) : DispatchOutcome
    }
}
