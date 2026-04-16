package com.opensmarthome.speaker.tool.system

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

data class TimerInfo(
    val id: String,
    val label: String,
    val remainingSeconds: Int,
    val totalSeconds: Int
)
