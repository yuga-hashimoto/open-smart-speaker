package com.opensmarthome.speaker.ui.ambient

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure data snapshot for an "Alexa Echo Show"-style ambient screen.
 * Aggregates clock, weather, recent notifications count, unread tasks, and
 * a short device summary. Kept DTO-only so UI layer can consume without
 * pulling heavy deps.
 *
 * Inspired by Ava's ambient display and ViewAssist's info panel.
 */
data class AmbientSnapshot(
    val nowMs: Long,
    val temperatureC: Double? = null,
    val humidityPercent: Int? = null,
    val weatherCondition: String? = null,
    val activeNotificationCount: Int = 0,
    val unreadTaskCount: Int = 0,
    val activeTimerCount: Int = 0,
    val nextTimerLabel: String? = null,
    val nextTimerRemainingSeconds: Int? = null,
    val recentDeviceActivity: List<DeviceLine> = emptyList(),
    val upcomingCalendarEvents: List<String> = emptyList(),
    /** Host device battery level 0..100, or null if not yet sampled. */
    val batteryLevel: Int? = null,
    /** True while the host device is plugged in. */
    val batteryCharging: Boolean = false,
    /** Host thermal bucket — NORMAL / WARM / HOT. Only surfaced when non-NORMAL. */
    val thermalBucket: String? = null
) {
    data class DeviceLine(val name: String, val state: String)

    fun formattedTime(locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("HH:mm", locale).format(Date(nowMs))

    fun formattedDate(locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("EEE, MMM d", locale).format(Date(nowMs))

    fun greeting(locale: Locale = Locale.getDefault()): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
        return when (cal.get(java.util.Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
    }
}
