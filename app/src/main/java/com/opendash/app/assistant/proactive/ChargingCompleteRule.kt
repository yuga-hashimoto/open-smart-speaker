package com.opendash.app.assistant.proactive

import com.opendash.app.util.BatteryStatus

/**
 * Gentle "you can unplug now" nudge for the tablet itself. Fires when
 * the battery is at or above [FULL_THRESHOLD] and the device is still
 * plugged in. Leaving a lithium cell at 100% with the charger attached
 * accelerates wear; the Echo Show and Pixel Tablet both surface
 * similar reminders.
 *
 * Single-shot per charging session: the suggestion id is stable while
 * the device remains charging + full, so `SuggestionState` won't
 * re-surface it. Unplugging the cable resets the state; the rule
 * fires again on the next charge-to-full cycle.
 *
 * Low priority — this is a quality-of-life nudge, not a warning.
 */
class ChargingCompleteRule(
    private val statusSupplier: () -> BatteryStatus,
) : SuggestionRule {

    override suspend fun evaluate(context: ProactiveContext): Suggestion? {
        val status = statusSupplier()
        if (!status.isCharging) return null
        if (status.level < FULL_THRESHOLD) return null
        return Suggestion(
            id = "charging_complete",
            priority = Suggestion.Priority.LOW,
            message = "Tablet is fully charged. You can unplug the cable " +
                "to help the battery last longer.",
            suggestedAction = null,
            expiresAtMs = context.nowMs + EXPIRY_WINDOW_MS,
        )
    }

    companion object {
        /** 100% is the trigger. Picking 95 would fire before the charge
         *  controller is done topping up and annoy the user. */
        internal const val FULL_THRESHOLD = 100

        /** Suggestion stays visible for an hour. By then either the
         *  user has unplugged or they have decided not to; re-surfacing
         *  would be nagging. */
        private const val EXPIRY_WINDOW_MS: Long = 60L * 60L * 1000L
    }
}
