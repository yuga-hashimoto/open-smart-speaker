package com.opensmarthome.speaker.assistant.proactive

import com.opensmarthome.speaker.device.model.Device
import com.opensmarthome.speaker.device.model.DeviceType

/**
 * Once the clock is past [BEDTIME_HOUR], surface a suggestion if any
 * Home Assistant light is still reporting `isOn == true`. The intent
 * is the "did you forget to turn off the lamp in the living room?"
 * nudge — gentle, single-shot, actionable.
 *
 * Dedupe: the suggestion id mixes the set of on-light names so the
 * nudge re-surfaces only when the *set* of on-lights changes. That
 * way dismissing the card and then leaving the lights on won't spam
 * the user, but turning off some lights and leaving others on does
 * refresh the suggestion.
 *
 * The rule does not act — it only suggests. `execute_command` with
 * "turn off all lights" belongs to the user's acceptance tap.
 */
class ForgotLightsAtBedtimeRule(
    private val devicesSupplier: () -> Collection<Device>,
) : SuggestionRule {

    override suspend fun evaluate(context: ProactiveContext): Suggestion? {
        if (context.hourOfDay !in BEDTIME_HOURS) return null
        val onLights = devicesSupplier()
            .asSequence()
            .filter { it.type == DeviceType.LIGHT && it.state.isOn == true }
            .map { it.name }
            .sorted()
            .toList()
        if (onLights.isEmpty()) return null

        val idSuffix = onLights.joinToString(separator = "|")
        return Suggestion(
            id = "forgot_lights_$idSuffix",
            priority = Suggestion.Priority.NORMAL,
            message = buildMessage(onLights),
            suggestedAction = SuggestedAction(
                toolName = "execute_command",
                arguments = mapOf(
                    "device_type" to "light",
                    "action" to "turn_off",
                ),
            ),
            expiresAtMs = context.nowMs + EXPIRY_WINDOW_MS,
        )
    }

    private fun buildMessage(names: List<String>): String =
        when (names.size) {
            1 -> "${names.first()} is still on. Want me to turn it off?"
            2 -> "${names.joinToString(" and ")} are still on. Want me to turn them off?"
            else -> {
                val visible = names.take(2).joinToString(", ")
                val more = names.size - 2
                "$visible and $more other light${if (more == 1) "" else "s"} still on. Want me to turn them off?"
            }
        }

    companion object {
        /** Earliest hour (inclusive) at which we consider a light left
         *  on to be "forgotten". Matches the existing `NightQuietRule`. */
        internal const val BEDTIME_HOUR: Int = 22

        /** Range of hours in which the rule fires. Wraps midnight so a
         *  user up at 1 AM still sees the nudge. */
        internal val BEDTIME_HOURS: Set<Int> = setOf(22, 23, 0, 1, 2)

        /** Half-hour expiry so dismissal lasts long enough to make the
         *  user's next round of the house but refreshes within one
         *  sleep cycle if the lights really are still on. */
        private const val EXPIRY_WINDOW_MS: Long = 30L * 60L * 1000L
    }
}
