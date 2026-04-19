package com.opendash.app.tool.system

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

/**
 * LLM tool: schedule an alarm in the user's clock app via AlarmClock intent.
 * Requires com.android.alarm.permission.SET_ALARM (auto-granted on install).
 * The intent is fire-and-forget; we cannot read it back to confirm.
 */
class SetAlarmToolExecutor(
    private val context: Context
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "set_alarm",
            description = "Schedule an alarm in the system clock app for the given time of day. Skips the confirmation screen — the alarm is created immediately.",
            parameters = mapOf(
                "hour" to ToolParameter("number", "Hour 0-23", required = true),
                "minute" to ToolParameter("number", "Minute 0-59", required = true),
                "label" to ToolParameter("string", "Optional alarm label (e.g. 'wake up')", required = false)
            )
        ),
        ToolSchema(
            name = "set_quick_timer",
            description = "Start a countdown timer in the system clock app. Use for short durations the user wants the OS to handle, separate from the in-app TimerManager.",
            parameters = mapOf(
                "seconds" to ToolParameter("number", "Duration in seconds (1-86400)", required = true),
                "label" to ToolParameter("string", "Optional timer label", required = false)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "set_alarm" -> executeSetAlarm(call)
                "set_quick_timer" -> executeSetTimer(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Alarm tool failed: ${call.name}")
            ToolResult(call.id, false, "", e.message ?: "Execution failed")
        }
    }

    private fun executeSetAlarm(call: ToolCall): ToolResult {
        val hour = (call.arguments["hour"] as? Number)?.toInt()
            ?: return ToolResult(call.id, false, "", "hour is required")
        val minute = (call.arguments["minute"] as? Number)?.toInt()
            ?: return ToolResult(call.id, false, "", "minute is required")
        if (hour !in 0..23) return ToolResult(call.id, false, "", "hour out of range: $hour")
        if (minute !in 0..59) return ToolResult(call.id, false, "", "minute out of range: $minute")

        val label = (call.arguments["label"] as? String)?.trim().orEmpty()

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (label.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return launch(call, intent) {
            """{"hour":$hour,"minute":${"%02d".format(minute)},"label":"${label.escapeJson()}"}"""
        }
    }

    private fun executeSetTimer(call: ToolCall): ToolResult {
        val seconds = (call.arguments["seconds"] as? Number)?.toInt()
            ?: return ToolResult(call.id, false, "", "seconds is required")
        if (seconds !in 1..86400) {
            return ToolResult(call.id, false, "", "seconds out of range: $seconds")
        }
        val label = (call.arguments["label"] as? String)?.trim().orEmpty()

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (label.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return launch(call, intent) {
            """{"seconds":$seconds,"label":"${label.escapeJson()}"}"""
        }
    }

    private inline fun launch(
        call: ToolCall,
        intent: Intent,
        success: () -> String
    ): ToolResult {
        return try {
            // Resolve first so we don't crash on devices without a clock app.
            if (intent.resolveActivity(context.packageManager) == null) {
                ToolResult(call.id, false, "", "No app on this device handles ${intent.action}")
            } else {
                context.startActivity(intent)
                ToolResult(call.id, true, success())
            }
        } catch (e: SecurityException) {
            ToolResult(call.id, false, "", "Blocked by SecurityException: ${e.message}")
        }
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")
}
