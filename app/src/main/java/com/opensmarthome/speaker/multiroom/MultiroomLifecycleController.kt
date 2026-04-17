package com.opensmarthome.speaker.multiroom

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Coordinates start/stop of the opt-in multi-room subsystem (mDNS
 * register + announcement server + peer liveness tracker) in response
 * to the `MULTIROOM_BROADCAST_ENABLED` preference toggling.
 *
 * Design goals:
 *  - **Idempotent:** repeated `setEnabled(true)` or `setEnabled(false)`
 *    calls (the [kotlinx.coroutines.flow.distinctUntilChanged] guard
 *    on preference flows already filters most, but defensive here) do
 *    not double-start or double-stop the subsystem.
 *  - **Crash-safe:** if `onStart` throws partway through the startup
 *    sequence, the controller stays inactive so the next toggle can
 *    retry cleanly. If `onStop` throws, the controller still flips to
 *    inactive — callers already run best-effort `runCatching { ... }`
 *    around teardown of each subsystem, so a stray exception should
 *    not leak up and wedge the observer coroutine.
 *  - **Single in-flight transition:** a [Mutex] serializes transitions
 *    so `setEnabled(true)` and `setEnabled(false)` racing from the
 *    preference flow can't interleave register/unregister calls.
 *
 * The split into `onStart` / `onStop` lambdas (rather than wiring the
 * three singletons directly) keeps the controller unit-testable on
 * pure JVM: the Android-only NsdManager / ServerSocket subsystems stay
 * in VoiceService.
 */
class MultiroomLifecycleController(
    private val onStart: suspend () -> Unit,
    private val onStop: suspend () -> Unit,
) {

    private val mutex = Mutex()

    @Volatile
    private var active: Boolean = false

    /** Exposed for tests and debug surfaces. */
    val isActive: Boolean
        get() = active

    suspend fun setEnabled(enabled: Boolean) {
        mutex.withLock {
            when {
                enabled && !active -> {
                    onStart()
                    active = true
                }
                !enabled && active -> {
                    try {
                        onStop()
                    } catch (t: Throwable) {
                        Timber.w(t, "MultiroomLifecycleController: onStop threw; still marking inactive")
                    }
                    active = false
                }
                else -> {
                    // Already in the desired state; nothing to do.
                }
            }
        }
    }
}
