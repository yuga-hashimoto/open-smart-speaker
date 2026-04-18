package com.opendash.app.tool.system

/**
 * Manages timers and alarms on the device.
 * Implementation uses Android AlarmManager / CountDownTimer.
 */
interface TimerManager {
    suspend fun setTimer(seconds: Int, label: String = ""): String
    suspend fun cancelTimer(timerId: String): Boolean
    suspend fun getActiveTimers(): List<TimerInfo>

    /** Cancel every active timer. Returns the number of timers cancelled. */
    suspend fun cancelAllTimers(): Int {
        var count = 0
        getActiveTimers().forEach { info ->
            if (cancelTimer(info.id)) count++
        }
        return count
    }
}

/**
 * Describes a pending or currently-firing timer.
 *
 * A timer lives through two phases:
 *  - counting down: [remainingSeconds] > 0, [isFiring] = false
 *  - firing / alarm ringing: [remainingSeconds] = 0, [isFiring] = true
 *
 * A firing timer stays in [TimerManager.getActiveTimers] until the user (or
 * voice / safety timeout) cancels it, so the UI and the voice cancel paths
 * both have a target to act on.
 */
data class TimerInfo(
    val id: String,
    val label: String,
    val remainingSeconds: Int,
    val totalSeconds: Int,
    val isFiring: Boolean = false
)
