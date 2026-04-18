package com.opendash.app.tool.system

import android.content.Context
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Schedules a delayed callback on the main thread. Abstracted so JVM unit
 * tests can provide an immediate / manually-driven implementation without
 * needing Robolectric to back [Handler].
 */
interface SafetyScheduler {
    /** Returns a cancel token that, when invoked, cancels the pending callback. */
    fun schedule(delayMs: Long, action: () -> Unit): () -> Unit
}

private class MainLooperSafetyScheduler : SafetyScheduler {
    private val handler = Handler(Looper.getMainLooper())
    override fun schedule(delayMs: Long, action: () -> Unit): () -> Unit {
        val runnable = Runnable { action() }
        handler.postDelayed(runnable, delayMs)
        return { handler.removeCallbacks(runnable) }
    }
}

/**
 * Android implementation of [TimerManager] using [CountDownTimer].
 *
 * ## Firing lifecycle
 * When a timer finishes counting down it enters the **firing** state instead
 * of disappearing. The entry stays in [getActiveTimers] (with
 * [TimerInfo.isFiring] = true) so the UI can show a "Stop" button and voice
 * cancel paths (`cancel_timer` / `cancel_all_timers`) still have a target.
 *
 * The alarm plays on loop via an [AlarmPlayer]. It stops when any of:
 *  - the user calls [cancelTimer] / [cancelAllTimers]
 *  - the safety cap ([maxFiringMs], default 5 min) elapses
 *
 * The old implementation removed the timer from [activeTimers] on fire and
 * played the ringtone once with no loop, which caused two bugs:
 *  - firing timer was uncancellable via UI or voice
 *  - ringtone auto-silenced after ~10 s (device-dependent)
 *
 * [AlarmPlayer] and [SafetyScheduler] are abstracted so this class is
 * exercisable from JVM unit tests without pulling in [android.media.MediaPlayer]
 * or a live [Looper]. The [CountDownTimer] itself still requires a Looper and
 * is only wired in production via [setTimer]; tests drive the firing lifecycle
 * through [fireTimer].
 */
class AndroidTimerManager(
    private val context: Context,
    private val alarmPlayerFactory: AlarmPlayerFactory,
    private val safetyScheduler: SafetyScheduler,
    private val maxFiringMs: Long = DEFAULT_MAX_FIRING_MS
) : TimerManager {

    /** Production constructor: default MediaPlayer-based alarm + main-looper scheduler. */
    constructor(context: Context) : this(
        context = context,
        alarmPlayerFactory = AlarmPlayerFactory { AndroidMediaPlayerAlarmPlayer(context) },
        safetyScheduler = MainLooperSafetyScheduler()
    )

    private val activeTimers = ConcurrentHashMap<String, ActiveTimer>()

    private data class ActiveTimer(
        val id: String,
        val label: String,
        val totalSeconds: Int,
        val startTimeMs: Long,
        val countDownTimer: CountDownTimer?,
        val isFiring: Boolean,
        val alarmPlayer: AlarmPlayer?,
        val cancelSafety: (() -> Unit)?
    )

    override suspend fun setTimer(seconds: Int, label: String): String {
        val id = "timer_${UUID.randomUUID().toString().take(8)}"

        val timer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                // Timer ticking
            }

            override fun onFinish() {
                fireTimer(id)
            }
        }

        activeTimers[id] = ActiveTimer(
            id = id,
            label = label,
            totalSeconds = seconds,
            startTimeMs = System.currentTimeMillis(),
            countDownTimer = timer,
            isFiring = false,
            alarmPlayer = null,
            cancelSafety = null
        )

        timer.start()
        Timber.d("Timer set: $id for $seconds seconds ($label)")
        return id
    }

    override suspend fun cancelTimer(timerId: String): Boolean {
        val existing = activeTimers.remove(timerId) ?: return false
        existing.countDownTimer?.cancel()
        existing.alarmPlayer?.stop()
        existing.cancelSafety?.invoke()
        Timber.d("Timer cancelled: $timerId (wasFiring=${existing.isFiring})")
        return true
    }

    override suspend fun getActiveTimers(): List<TimerInfo> {
        val now = System.currentTimeMillis()
        return activeTimers.values.map { timer ->
            if (timer.isFiring) {
                TimerInfo(
                    id = timer.id,
                    label = timer.label,
                    remainingSeconds = 0,
                    totalSeconds = timer.totalSeconds,
                    isFiring = true
                )
            } else {
                val elapsed = ((now - timer.startTimeMs) / 1000).toInt()
                val remaining = (timer.totalSeconds - elapsed).coerceAtLeast(0)
                TimerInfo(
                    id = timer.id,
                    label = timer.label,
                    remainingSeconds = remaining,
                    totalSeconds = timer.totalSeconds,
                    isFiring = false
                )
            }
        }
    }

    /**
     * Internal hook invoked by [CountDownTimer.onFinish] when a timer completes.
     * Also used directly by unit tests to drive the firing lifecycle without
     * needing a real Looper-backed CountDownTimer.
     */
    internal fun fireTimer(id: String) {
        val existing = activeTimers[id] ?: return
        if (existing.isFiring) return

        val player = runCatching { alarmPlayerFactory.create() }
            .onFailure { Timber.e(it, "AlarmPlayer factory threw for $id") }
            .getOrNull()
        runCatching { player?.startLooping() }
            .onFailure { Timber.e(it, "AlarmPlayer.startLooping threw for $id") }

        val cancelSafety = runCatching {
            safetyScheduler.schedule(maxFiringMs) {
                Timber.w("Timer $id hit firing safety cap; auto-stopping")
                val stopped = activeTimers.remove(id)
                stopped?.alarmPlayer?.stop()
            }
        }.getOrNull()

        activeTimers[id] = existing.copy(
            countDownTimer = null,
            isFiring = true,
            alarmPlayer = player,
            cancelSafety = cancelSafety
        )
        Timber.d("Timer fired: $id (${existing.label})")
    }

    companion object {
        /** Max time a firing alarm can ring before auto-silencing (5 minutes). */
        const val DEFAULT_MAX_FIRING_MS: Long = 5L * 60L * 1000L
    }
}
