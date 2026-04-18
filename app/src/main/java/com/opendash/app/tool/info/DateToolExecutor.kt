package com.opendash.app.tool.info

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Date arithmetic tool using only `java.time.LocalDate` (stdlib, no new deps).
 *
 * Exposes three tools:
 *  - `date_diff`: integer day-count between two ISO-8601 dates.
 *  - `add_days`: add (or subtract) an integer number of days from an ISO-8601 date.
 *  - `day_of_week`: weekday name (Monday..Sunday) for an ISO-8601 date.
 */
class DateToolExecutor : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "date_diff",
            description = "Days between two ISO-8601 dates (YYYY-MM-DD). Returns positive if 'to' is after 'from', negative otherwise. Example: date_diff('2026-04-17','2026-12-25') -> 252.",
            parameters = mapOf(
                "from" to ToolParameter("string", "ISO-8601 start date, e.g. 2026-04-17", required = true),
                "to" to ToolParameter("string", "ISO-8601 end date, e.g. 2026-12-25", required = true)
            )
        ),
        ToolSchema(
            name = "add_days",
            description = "Add an integer number of days (can be negative) to an ISO-8601 date. Days must be within [-36500, 36500]. Example: add_days('2026-04-17', 30) -> '2026-05-17'.",
            parameters = mapOf(
                "date" to ToolParameter("string", "ISO-8601 date, e.g. 2026-04-17", required = true),
                "days" to ToolParameter("integer", "Integer number of days to add (negative to subtract). Range [-36500, 36500].", required = true)
            )
        ),
        ToolSchema(
            name = "day_of_week",
            description = "Return the weekday name (Monday..Sunday) for an ISO-8601 date. Example: day_of_week('2026-04-17') -> 'Friday'.",
            parameters = mapOf(
                "date" to ToolParameter("string", "ISO-8601 date, e.g. 2026-04-17", required = true)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "date_diff" -> executeDateDiff(call)
                "add_days" -> executeAddDays(call)
                "day_of_week" -> executeDayOfWeek(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Date tool failed")
            ToolResult(call.id, false, "", e.message ?: "Date tool failed")
        }
    }

    private fun executeDateDiff(call: ToolCall): ToolResult {
        val fromStr = call.arguments["from"] as? String
            ?: return ToolResult(call.id, false, "", "Missing 'from' date")
        val toStr = call.arguments["to"] as? String
            ?: return ToolResult(call.id, false, "", "Missing 'to' date")

        val fromDate = parseIsoDate(fromStr)
            ?: return ToolResult(call.id, false, "", "Invalid ISO-8601 date for 'from': $fromStr")
        val toDate = parseIsoDate(toStr)
            ?: return ToolResult(call.id, false, "", "Invalid ISO-8601 date for 'to': $toStr")

        val days = ChronoUnit.DAYS.between(fromDate, toDate)
        return ToolResult(
            call.id, true,
            """{"from":"${fromDate.format(ISO)}","to":"${toDate.format(ISO)}","days":$days}"""
        )
    }

    private fun executeAddDays(call: ToolCall): ToolResult {
        val dateStr = call.arguments["date"] as? String
            ?: return ToolResult(call.id, false, "", "Missing 'date'")
        val daysAny = call.arguments["days"]
            ?: return ToolResult(call.id, false, "", "Missing 'days'")

        val days = toLong(daysAny)
            ?: return ToolResult(call.id, false, "", "'days' must be an integer, got: $daysAny")
        if (days < MIN_DAYS || days > MAX_DAYS) {
            return ToolResult(
                call.id, false, "",
                "'days' out of range [$MIN_DAYS, $MAX_DAYS]: $days"
            )
        }
        val date = parseIsoDate(dateStr)
            ?: return ToolResult(call.id, false, "", "Invalid ISO-8601 date: $dateStr")

        val result = date.plusDays(days)
        return ToolResult(
            call.id, true,
            """{"date":"${date.format(ISO)}","days":$days,"result":"${result.format(ISO)}"}"""
        )
    }

    private fun executeDayOfWeek(call: ToolCall): ToolResult {
        val dateStr = call.arguments["date"] as? String
            ?: return ToolResult(call.id, false, "", "Missing 'date'")
        val date = parseIsoDate(dateStr)
            ?: return ToolResult(call.id, false, "", "Invalid ISO-8601 date: $dateStr")

        val dayName = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        return ToolResult(
            call.id, true,
            """{"date":"${date.format(ISO)}","day_of_week":"$dayName"}"""
        )
    }

    /**
     * Parse a strict ISO-8601 local date (YYYY-MM-DD). Returns null on any format or value error.
     */
    private fun parseIsoDate(input: String): LocalDate? {
        return try {
            LocalDate.parse(input, ISO)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /**
     * Coerce numeric-ish inputs to Long. Accepts Int/Long/Short/Byte and strings that parse as
     * integers, but rejects floating point values that are not whole numbers.
     */
    private fun toLong(value: Any?): Long? = when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Short -> value.toLong()
        is Byte -> value.toLong()
        is Double -> if (value % 1.0 == 0.0 && value.isFinite()) value.toLong() else null
        is Float -> if (value % 1.0f == 0.0f && value.isFinite()) value.toLong() else null
        is String -> value.trim().toLongOrNull()
        else -> null
    }

    companion object {
        private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        private const val MIN_DAYS = -36_500L
        private const val MAX_DAYS = 36_500L
    }
}
