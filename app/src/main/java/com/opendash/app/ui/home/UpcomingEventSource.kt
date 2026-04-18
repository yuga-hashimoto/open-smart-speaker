package com.opendash.app.ui.home

import com.opendash.app.tool.system.CalendarEvent
import com.opendash.app.tool.system.CalendarProvider
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks the single next upcoming calendar event for the Home dashboard.
 *
 * Minimal surface — the Home card only ever shows one event at a time
 * (the very next meeting / appointment). For the full list, users open
 * the calendar tool via voice. Keeping the interface single-shot lets
 * the ViewModel treat it as a plain `suspend` fetch rather than wiring
 * up a Flow.
 *
 * Mirrors [OnlineBriefingSource] so Home state sources have a consistent
 * look: an interface with a no-op `Empty` fallback + a Hilt-backed
 * `Default` impl delegating to an existing provider.
 */
interface UpcomingEventSource {
    suspend fun nextEvent(): CalendarEvent?

    object Empty : UpcomingEventSource {
        override suspend fun nextEvent(): CalendarEvent? = null
    }
}

/**
 * Default impl: pulls the next 24 hours of events from [CalendarProvider],
 * filters to "still in the future or currently running", and returns the
 * earliest. Returns `null` when permission is missing or the fetch errors.
 */
@Singleton
class DefaultUpcomingEventSource @Inject constructor(
    private val calendarProvider: CalendarProvider,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : UpcomingEventSource {

    override suspend fun nextEvent(): CalendarEvent? {
        if (!calendarProvider.hasPermission()) return null
        val events = runCatching { calendarProvider.getUpcomingEvents(LOOKAHEAD_DAYS) }
            .onFailure { Timber.w(it, "UpcomingEventSource: calendar fetch failed") }
            .getOrNull()
            ?: return null
        val now = nowMs()
        return events.asSequence()
            .filter { it.endMs > now }
            .minByOrNull { it.startMs }
    }

    companion object {
        /** 1 day ahead — Home only surfaces the immediate next event;
         *  pulling a week would waste work. */
        private const val LOOKAHEAD_DAYS: Int = 1
    }
}
