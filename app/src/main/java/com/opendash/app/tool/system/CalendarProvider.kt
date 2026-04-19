package com.opendash.app.tool.system

/**
 * Reads calendar events.
 * Implementation uses Android CalendarContract (requires READ_CALENDAR permission).
 */
interface CalendarProvider {
    suspend fun getUpcomingEvents(daysAhead: Int): List<CalendarEvent>
    suspend fun getEventsBetween(startMs: Long, endMs: Long): List<CalendarEvent>
    fun hasPermission(): Boolean

    /** True if WRITE_CALENDAR is granted. */
    fun hasWritePermission(): Boolean = false

    /**
     * Insert an event into the user's primary writable calendar.
     * Returns the event id on success, null on failure or missing permission.
     */
    suspend fun createEvent(
        title: String,
        startMs: Long,
        endMs: Long,
        location: String? = null,
        description: String? = null,
        allDay: Boolean = false
    ): Long? = null
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
