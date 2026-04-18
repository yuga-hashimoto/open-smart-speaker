package com.opendash.app.tool.system

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * Android implementation of CalendarProvider using CalendarContract.
 * Requires READ_CALENDAR permission.
 */
class AndroidCalendarProvider(
    private val context: Context
) : CalendarProvider {

    override suspend fun getUpcomingEvents(daysAhead: Int): List<CalendarEvent> {
        val now = System.currentTimeMillis()
        val end = now + daysAhead.coerceIn(1, 90) * 24L * 60 * 60 * 1000
        return getEventsBetween(now, end)
    }

    override suspend fun getEventsBetween(startMs: Long, endMs: Long): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, startMs)
            ContentUris.appendId(this, endMs)
        }.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME
        )

        val events = mutableListOf<CalendarEvent>()
        try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext() && events.size < 50) {
                    events.add(
                        CalendarEvent(
                            id = cursor.getLong(0),
                            title = cursor.getString(1).orEmpty(),
                            description = cursor.getString(2).orEmpty(),
                            location = cursor.getString(3).orEmpty(),
                            startMs = cursor.getLong(4),
                            endMs = cursor.getLong(5),
                            allDay = cursor.getInt(6) == 1,
                            calendarName = cursor.getString(7).orEmpty()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to query calendar events")
        }

        return events
    }

    override fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }
}
