package com.opendash.app.tool.system

/**
 * Reads calendar events.
 * Implementation uses Android CalendarContract (requires READ_CALENDAR permission).
 */
interface CalendarProvider {
    suspend fun getUpcomingEvents(daysAhead: Int): List<CalendarEvent>
    suspend fun getEventsBetween(startMs: Long, endMs: Long): List<CalendarEvent>
    fun hasPermission(): Boolean
}

data class CalendarEvent(
    val id: Long,
    val title: String,
    val description: String,
    val location: String,
    val startMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val calendarName: String
)
