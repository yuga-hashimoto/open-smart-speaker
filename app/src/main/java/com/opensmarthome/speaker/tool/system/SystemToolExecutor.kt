package com.opensmarthome.speaker.tool.system

import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolParameter
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.tool.ToolSchema
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Executes Android system tools: timers, volume, app launcher, datetime.
 * Bridges LLM tool calls to Android system APIs.
 */
class SystemToolExecutor(
    private val timerManager: TimerManager,
    private val volumeManager: VolumeManager,
    private val appLauncher: AppLauncher? = null
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "set_timer",
            description = "Set a countdown timer. Specify duration in seconds and an optional label.",
            parameters = mapOf(
                "seconds" to ToolParameter("number", "Duration in seconds", required = true),
                "label" to ToolParameter("string", "Timer label", required = false)
            )
        ),
        ToolSchema(
            name = "cancel_timer",
            description = "Cancel an active timer by its ID.",
            parameters = mapOf(
                "timer_id" to ToolParameter("string", "The timer ID to cancel", required = true)
            )
        ),
        ToolSchema(
            name = "cancel_all_timers",
            description = "Cancel every active timer.",
            parameters = emptyMap()
        ),
        ToolSchema(
            name = "get_timers",
            description = "List all active timers.",
            parameters = emptyMap()
        ),
        ToolSchema(
            name = "set_volume",
            description = "Set the device volume level (0-100).",
            parameters = mapOf(
                "level" to ToolParameter("number", "Volume level 0-100", required = true)
            )
        ),
        ToolSchema(
            name = "get_volume",
            description = "Get the current device volume level.",
            parameters = emptyMap()
        ),
        ToolSchema(
            name = "get_datetime",
            description = "Get the current date, time, and day of week.",
            parameters = emptyMap()
        ),
        ToolSchema(
            name = "launch_app",
            description = "Open an app on the device by name (e.g. 'YouTube', 'Chrome', 'Settings').",
            parameters = mapOf(
                "app_name" to ToolParameter("string", "The app name to launch", required = true)
            )
        ),
        ToolSchema(
            name = "list_apps",
            description = "List installed apps on the device.",
            parameters = emptyMap()
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "set_timer" -> executeSetTimer(call)
                "cancel_timer" -> executeCancelTimer(call)
                "cancel_all_timers" -> executeCancelAllTimers(call)
                "get_timers" -> executeGetTimers(call)
                "set_volume" -> executeSetVolume(call)
                "get_volume" -> executeGetVolume(call)
                "get_datetime" -> executeGetDatetime(call)
                "launch_app" -> executeLaunchApp(call)
                "list_apps" -> executeListApps(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "System tool execution failed: ${call.name}")
            ToolResult(call.id, false, "", e.message ?: "Execution failed")
        }
    }

    private suspend fun executeSetTimer(call: ToolCall): ToolResult {
        val seconds = (call.arguments["seconds"] as? Number)?.toInt()
            ?: return ToolResult(call.id, false, "", "Missing seconds parameter")
        val label = call.arguments["label"] as? String ?: ""

        val timerId = timerManager.setTimer(seconds, label)
        return ToolResult(
            call.id, true,
            """{"timer_id": "$timerId", "seconds": $seconds, "label": "$label"}"""
        )
    }

    private suspend fun executeCancelTimer(call: ToolCall): ToolResult {
        val timerId = call.arguments["timer_id"] as? String
            ?: return ToolResult(call.id, false, "", "Missing timer_id")

        val success = timerManager.cancelTimer(timerId)
        return if (success) {
            ToolResult(call.id, true, """{"cancelled": "$timerId"}""")
        } else {
            ToolResult(call.id, false, "", "Timer not found: $timerId")
        }
    }

    private suspend fun executeCancelAllTimers(call: ToolCall): ToolResult {
        val count = timerManager.cancelAllTimers()
        return ToolResult(call.id, true, """{"cancelled_count": $count}""")
    }

    private suspend fun executeGetTimers(call: ToolCall): ToolResult {
        val timers = timerManager.getActiveTimers()
        val data = timers.joinToString(",") { t ->
            """{"id":"${t.id}","label":"${t.label}","remaining":${t.remainingSeconds},"total":${t.totalSeconds}}"""
        }
        return ToolResult(call.id, true, "[$data]")
    }

    private suspend fun executeSetVolume(call: ToolCall): ToolResult {
        val level = (call.arguments["level"] as? Number)?.toInt()
            ?: return ToolResult(call.id, false, "", "Missing level parameter")

        val clamped = level.coerceIn(0, 100)
        val success = volumeManager.setVolume(clamped)
        return if (success) {
            ToolResult(call.id, true, """{"volume": $clamped}""")
        } else {
            ToolResult(call.id, false, "", "Failed to set volume")
        }
    }

    private suspend fun executeGetVolume(call: ToolCall): ToolResult {
        val level = volumeManager.getVolume()
        return ToolResult(call.id, true, """{"volume": $level}""")
    }

    private fun executeGetDatetime(call: ToolCall): ToolResult {
        val now = Date()
        val tz = TimeZone.getDefault()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        val dayFormat = SimpleDateFormat("EEEE", Locale.US)

        return ToolResult(
            call.id, true,
            """{"date":"${dateFormat.format(now)}","time":"${timeFormat.format(now)}","day":"${dayFormat.format(now)}","timezone":"${tz.id}"}"""
        )
    }

    private suspend fun executeLaunchApp(call: ToolCall): ToolResult {
        val launcher = appLauncher
            ?: return ToolResult(call.id, false, "", "App launcher not available")
        val appName = call.arguments["app_name"] as? String
            ?: return ToolResult(call.id, false, "", "Missing app_name")

        val success = launcher.launchApp(appName)
        return if (success) {
            ToolResult(call.id, true, """{"launched":"$appName"}""")
        } else {
            ToolResult(call.id, false, "", "App not found: $appName")
        }
    }

    private suspend fun executeListApps(call: ToolCall): ToolResult {
        val launcher = appLauncher
            ?: return ToolResult(call.id, false, "", "App launcher not available")
        val apps = launcher.listInstalledApps()
        val data = apps.joinToString(",") { """{"name":"${it.name}","package":"${it.packageName}"}""" }
        return ToolResult(call.id, true, "[$data]")
    }
}
