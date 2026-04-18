package com.opendash.app.tool.info

import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.data.preferences.PreferenceKeys
import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Executes weather-related tools: get_weather, get_forecast.
 *
 * Location resolution order:
 * 1. Explicit `location` argument from the tool call (e.g. the fast-path
 *    matcher extracted "宗像市" from the utterance).
 * 2. User's configured `DEFAULT_LOCATION` preference (Settings screen).
 * 3. `null` — let the provider use its own built-in fallback ("Tokyo",
 *    preserved for backward compat with installs that never set a default).
 */
class WeatherToolExecutor(
    private val weatherProvider: WeatherProvider,
    private val preferences: AppPreferences? = null
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "get_weather",
            description = "Get current weather conditions for a location. Returns temperature, condition, humidity, and wind.",
            parameters = mapOf(
                "location" to ToolParameter(
                    "string",
                    "City name or location (optional, defaults to device location)",
                    required = false
                )
            )
        ),
        ToolSchema(
            name = "get_forecast",
            description = "Get weather forecast for upcoming days.",
            parameters = mapOf(
                "location" to ToolParameter("string", "City name or location", required = false),
                "days" to ToolParameter("number", "Number of days (1-7)", required = false)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "get_weather" -> executeGetWeather(call)
                "get_forecast" -> executeGetForecast(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Weather tool execution failed: ${call.name}")
            ToolResult(call.id, false, "", e.message ?: "Execution failed")
        }
    }

    private suspend fun executeGetWeather(call: ToolCall): ToolResult {
        val location = resolveLocation(call.arguments["location"] as? String)
        val info = weatherProvider.getCurrent(location)
        val data = """{"location":"${info.location}","temperature_c":${info.temperatureC},"condition":"${info.condition}","humidity":${info.humidity},"wind_kph":${info.windKph}}"""
        return ToolResult(call.id, true, data)
    }

    private suspend fun executeGetForecast(call: ToolCall): ToolResult {
        val location = resolveLocation(call.arguments["location"] as? String)
        val days = (call.arguments["days"] as? Number)?.toInt()?.coerceIn(1, 7) ?: 3
        val forecast = weatherProvider.getForecast(location, days)
        val data = forecast.joinToString(",") { f ->
            """{"date":"${f.date}","min_c":${f.minTempC},"max_c":${f.maxTempC},"condition":"${f.condition}"}"""
        }
        return ToolResult(call.id, true, "[$data]")
    }

    /**
     * Pick the best location for the call: explicit argument wins, then the
     * user's saved default location, then null (provider built-in fallback).
     */
    private suspend fun resolveLocation(explicit: String?): String? {
        val explicitTrimmed = explicit?.trim()?.takeIf { it.isNotEmpty() }
        if (explicitTrimmed != null) return explicitTrimmed

        val prefs = preferences ?: return null
        val fromPref = runCatching {
            prefs.observe(PreferenceKeys.DEFAULT_LOCATION).first()
        }.getOrNull()
        return fromPref?.trim()?.takeIf { it.isNotEmpty() }
    }
}
