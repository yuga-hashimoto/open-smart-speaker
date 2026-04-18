package com.opendash.app.tool.system

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LLM tool for reading device calendar events.
 * Inspired by OpenClaw's calendar.* commands.
 */
class CalendarToolExecutor(
    private val calendarProvider: CalendarProvider
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "get_calendar_events",
            description = "Get upcoming calendar events for the next N days. Returns title, time, location, and description.",
            parameters = mapOf(
                "days_ahead" to ToolParameter(
                    "number",
                    "How many days forward to look (1-90, default 7)",
                    required = false
                )
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "get_calendar_events" -> executeGetEvents(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Calendar tool failed: ${call.name}")
            ToolResult(call.id, false, "", e.message ?: "Execution failed")
        }
    }

    private suspend fun executeGetEvents(call: ToolCall): ToolResult {
        if (!calendarProvider.hasPermission()) {
            return ToolResult(
                call.id, false, "",
                "Calendar permission not granted. Ask user to grant READ_CALENDAR in settings."
            )
        }

        val days = (call.arguments["days_ahead"] as? Number)?.toInt() ?: 7
        val events = calendarProvider.getUpcomingEvents(days)

        val data = events.joinToString(",") { e ->
            val start = formatTime(e.startMs, e.allDay)
            val end = formatTime(e.endMs, e.allDay)
            """{"id":${e.id},"title":"${e.title.escapeJson()}","start":"$start","end":"$end","location":"${e.location.escapeJson()}","description":"${e.description.escapeJson()}","calendar":"${e.calendarName.escapeJson()}","all_day":${e.allDay}}"""
        }
        return ToolResult(call.id, true, "[$data]")
    }

    private fun formatTime(ms: Long, allDay: Boolean): String {
        val fmt = if (allDay) {
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
        } else {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        }
        return fmt.format(Date(ms))
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
}
