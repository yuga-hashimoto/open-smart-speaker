package com.opendash.app.assistant.proactive

import com.opendash.app.util.BatteryStatus

/**
 * Proactive suggestion that nudges the user to plug the tablet in when
 * the battery drops below [LOW_THRESHOLD] while unplugged. A second
 * (more urgent) trip point at [CRITICAL_THRESHOLD] bumps the priority
 * so the card stands out when the tablet is close to shutting down.
 *
 * The rule reads `BatteryStatus` through an injected supplier so the
 * unit-test suite can feed synthetic samples without constructing a
 * real `BatteryMonitor` (which registers an Android BroadcastReceiver
 * unavailable on pure JVM).
 *
 * Dedupe: `Suggestion.id` is stable across levels inside the same
 * bucket (low vs critical) so `SuggestionState` will not re-surface
 * the same card on every poll.
 */
class LowBatteryRule(
    private val statusSupplier: () -> BatteryStatus,
) : SuggestionRule {

    override suspend fun evaluate(context: ProactiveContext): Suggestion? {
        val status = statusSupplier()
        if (status.isCharging) return null
        val level = status.level
        return when {
            level < 0 -> null
            level <= CRITICAL_THRESHOLD -> Suggestion(
                id = "low_battery_critical",
                priority = Suggestion.Priority.HIGH,
                message = "Battery is at $level%. Please plug in soon — voice " +
                    "wake-word will pause below the saver threshold.",
                suggestedAction = null,
                expiresAtMs = context.nowMs + EXPIRY_WINDOW_MS,
            )
            level <= LOW_THRESHOLD -> Suggestion(
                id = "low_battery_low",
                priority = Suggestion.Priority.NORMAL,
                message = "Battery is at $level%. Consider plugging in the tablet.",
                suggestedAction = null,
                expiresAtMs = context.nowMs + EXPIRY_WINDOW_MS,
            )
            else -> null
        }
    }

    companion object {
        /** Matches `BatteryStatus.isLow` default trip point (20%) — this
         *  rule fires at or below the same threshold the VoiceService
         *  uses to throttle wake-word. */
        internal const val LOW_THRESHOLD = 20

        /** Below this the tablet is about to shut down — bump priority. */
        internal const val CRITICAL_THRESHOLD = 10

        /** Window after which the suggestion expires and can re-surface. */
        private const val EXPIRY_WINDOW_MS: Long = 10L * 60L * 1000L
    }
}
