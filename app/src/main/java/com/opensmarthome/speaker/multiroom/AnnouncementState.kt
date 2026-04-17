package com.opensmarthome.speaker.multiroom

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent banner state for the `announcement` envelope type.
 *
 * Unlike `tts_broadcast` (ephemeral — speak once and move on), an
 * `announcement` must **also** surface as a dismissable card on the
 * Ambient screen for `ttl_seconds` so household members who walked
 * into the room after the message can still see it. This is the
 * Alexa "household announcements" behaviour — speak + linger.
 *
 * The state is a [StateFlow] so the Compose UI can collect it reactively.
 * Auto-clear is scheduled on a [CoroutineScope] — re-announcing replaces
 * the banner and resets the timer so the most recent message stays
 * visible for its full TTL even if a new announcement supersedes an
 * in-flight one.
 *
 * Scope lives for the lifetime of the @Singleton. Tests inject a
 * `TestScope` via the secondary constructor to drive virtual time with
 * `advanceTimeBy`; production code relies on the Hilt-provided default
 * (Dispatchers.Default + SupervisorJob).
 */
@Singleton
class AnnouncementState(
    private val scope: CoroutineScope
) {

    @Inject
    constructor() : this(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private val _active = MutableStateFlow<ActiveAnnouncement?>(null)
    val activeAnnouncement: StateFlow<ActiveAnnouncement?> = _active.asStateFlow()

    @Volatile
    private var clearJob: Job? = null

    /**
     * Monotonic counter incremented on each [setAnnouncement] call. Lets the
     * auto-clear coroutine verify that the banner it was scheduled to clear
     * is still the banner on screen — using wall-clock `System.currentTimeMillis`
     * here was too coarse under `runTest`'s virtual time, which advances
     * virtual time without moving the real clock.
     */
    private var generation: Long = 0L

    /**
     * Publish [text] as the active announcement for [ttlSeconds]. Replaces
     * any banner currently on screen (the newest wins — a fresh announcement
     * that overrides the old one shouldn't be hidden by the older auto-clear).
     * Auto-clears after [ttlSeconds].
     */
    fun setAnnouncement(text: String, ttlSeconds: Int, from: String?) {
        if (text.isBlank() || ttlSeconds <= 0) return
        clearJob?.cancel()
        val myGeneration = ++generation
        val published = ActiveAnnouncement(
            text = text,
            ttlSeconds = ttlSeconds,
            from = from?.takeIf { it.isNotBlank() },
            receivedAtMs = System.currentTimeMillis()
        )
        _active.value = published
        clearJob = scope.launch {
            delay(ttlSeconds * 1000L)
            // Only clear if we're still the most recent banner — a newer
            // setAnnouncement() will have bumped [generation] past ours.
            if (generation == myGeneration) {
                _active.value = null
            }
        }
    }

    /** Manually dismiss the banner (e.g. user taps to dismiss on the Ambient screen). */
    fun clear() {
        clearJob?.cancel()
        clearJob = null
        _active.value = null
    }
}

/**
 * DTO for an active announcement banner. Kept separate from the wire
 * envelope because [text] + [from] are the only fields the UI cares
 * about, and we don't want to pin the UI to the protocol.
 */
data class ActiveAnnouncement(
    val text: String,
    val ttlSeconds: Int,
    /** Sender mDNS service name; nullable when the envelope omitted it. */
    val from: String?,
    /** Wall-clock millis at which this banner was published — used for auto-clear coalescing. */
    val receivedAtMs: Long
)
