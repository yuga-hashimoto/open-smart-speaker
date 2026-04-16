package com.opensmarthome.speaker.assistant.proactive

import timber.log.Timber
import java.util.Calendar

class SuggestionEngine(
    private val rules: List<SuggestionRule>
) {

    suspend fun evaluate(): List<Suggestion> {
        val context = buildContext()
        return rules.mapNotNull { rule ->
            try {
                rule.evaluate(context)
            } catch (e: Exception) {
                Timber.w(e, "Rule ${rule.javaClass.simpleName} failed")
                null
            }
        }.sortedByDescending { it.priority.ordinal }
    }

    private fun buildContext(): ProactiveContext {
        val cal = Calendar.getInstance()
        return ProactiveContext(
            nowMs = System.currentTimeMillis(),
            hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
            dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        )
    }
}

/** Morning greeting: 6-9 AM. */
class MorningGreetingRule : SuggestionRule {
    override suspend fun evaluate(context: ProactiveContext): Suggestion? {
        return if (context.hourOfDay in 6..9) {
            Suggestion(
                id = "morning_greeting_${context.hourOfDay}",
                priority = Suggestion.Priority.LOW,
                message = "Good morning. Would you like a weather briefing?",
                suggestedAction = SuggestedAction("get_weather", emptyMap())
            )
        } else null
    }
}

/** Evening suggestion: 18-22 dim-the-lights prompt. */
class EveningLightsRule : SuggestionRule {
    override suspend fun evaluate(context: ProactiveContext): Suggestion? {
        return if (context.hourOfDay in 18..22) {
            Suggestion(
                id = "evening_lights_${context.hourOfDay}",
                priority = Suggestion.Priority.LOW,
                message = "It's getting late. Want me to dim the lights?",
                // Tap "Yes" → dim all lights to 30% via execute_command
                suggestedAction = SuggestedAction(
                    toolName = "execute_command",
                    arguments = mapOf(
                        "device_type" to "light",
                        "action" to "set_brightness",
                        "parameters" to mapOf("brightness" to 30)
                    )
                )
            )
        } else null
    }
}

/** Weekend morning: relaxed greeting + weather forecast. */
class WeekendMorningRule : SuggestionRule {
    override suspend fun evaluate(context: ProactiveContext): Suggestion? {
        val isWeekend = context.dayOfWeek == java.util.Calendar.SATURDAY ||
            context.dayOfWeek == java.util.Calendar.SUNDAY
        return if (isWeekend && context.hourOfDay in 8..11) {
            Suggestion(
                id = "weekend_morning_${context.dayOfWeek}",
                priority = Suggestion.Priority.LOW,
                message = "Happy weekend. Want a forecast for the day?",
                suggestedAction = SuggestedAction(
                    toolName = "get_forecast",
                    arguments = emptyMap()
                )
            )
        } else null
    }
}

/**
 * Evening briefing: 19-21 — proactive notifications + calendar + timers
 * summary so the user knows what to expect overnight before going to bed.
 * NORMAL priority so it surfaces above ambient morning/evening greetings.
 */
class EveningBriefingRule : SuggestionRule {
    override suspend fun evaluate(context: ProactiveContext): Suggestion? {
        return if (context.hourOfDay in 19..21) {
            Suggestion(
                id = "evening_briefing_${context.hourOfDay}",
                priority = Suggestion.Priority.NORMAL,
                message = "Want a quick evening briefing — notifications, " +
                    "tomorrow's events, and active timers?",
                suggestedAction = SuggestedAction(
                    toolName = "evening_briefing",
                    arguments = emptyMap()
                )
            )
        } else null
    }
}

/** Night mode: suggest silent/do-not-disturb after 23:00. */
class NightQuietRule : SuggestionRule {
    override suspend fun evaluate(context: ProactiveContext): Suggestion? {
        return if (context.hourOfDay >= 23 || context.hourOfDay <= 4) {
            Suggestion(
                id = "night_quiet",
                priority = Suggestion.Priority.NORMAL,
                message = "It's late. Should I lower the volume and dim displays?",
                suggestedAction = SuggestedAction("set_volume", mapOf("level" to 20))
            )
        } else null
    }
}
