package com.opendash.app.tool.composite

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber
import java.util.UUID

/**
 * Composite "morning briefing" — runs weather + news + calendar via the
 * underlying tool executor and concatenates the results into a single JSON
 * payload the LLM can summarize for the user.
 *
 * Saves the LLM ~3 round trips for the most common voice-first
 * "good morning, what's going on today?" flow.
 *
 * The underlying [executor] is the full CompositeToolExecutor so this
 * composite can call any registered tool. Inject lazily to avoid a Hilt cycle.
 */
class MorningBriefingTool(
    private val executor: () -> ToolExecutor
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "morning_briefing",
            description = "Compose a morning briefing — runs get_weather, get_news, and get_calendar_events together and returns a combined JSON payload the assistant can summarize aloud.",
            parameters = mapOf(
                "include_news" to ToolParameter(
                    "boolean",
                    "Include news headlines (default true)",
                    required = false
                ),
                "include_weather" to ToolParameter(
                    "boolean",
                    "Include weather (default true)",
                    required = false
                ),
                "include_calendar" to ToolParameter(
                    "boolean",
                    "Include today's calendar events (default true)",
                    required = false
                )
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        if (call.name != "morning_briefing") {
            return ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
        }
        val includeNews = call.arguments["include_news"] as? Boolean ?: true
        val includeWeather = call.arguments["include_weather"] as? Boolean ?: true
        val includeCalendar = call.arguments["include_calendar"] as? Boolean ?: true

        val parts = mutableListOf<String>()
        val inner = executor()

        suspend fun callOpt(name: String, key: String) {
            try {
                val r = inner.execute(
                    ToolCall(
                        id = "mb_${UUID.randomUUID()}",
                        name = name,
                        arguments = emptyMap()
                    )
                )
                parts += "\"$key\":" + (if (r.success) r.data else "null")
            } catch (e: Exception) {
                Timber.w(e, "morning_briefing inner call failed: $name")
                parts += "\"$key\":null"
            }
        }

        if (includeWeather) callOpt("get_weather", "weather")
        if (includeNews) callOpt("get_news", "news")
        if (includeCalendar) callOpt("get_calendar_events", "calendar")

        return ToolResult(call.id, true, "{${parts.joinToString(",")}}")
    }
}
