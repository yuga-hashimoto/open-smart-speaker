package com.opensmarthome.speaker.ui.home

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.tool.system.CalendarEvent
import com.opensmarthome.speaker.tool.system.CalendarProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultUpcomingEventSourceTest {

    private val now = 1_700_000_000_000L

    private class FakeCalendarProvider(
        private val granted: Boolean = true,
        private val events: List<CalendarEvent> = emptyList(),
        private val error: Throwable? = null,
    ) : CalendarProvider {
        override suspend fun getUpcomingEvents(daysAhead: Int): List<CalendarEvent> {
            error?.let { throw it }
            return events
        }

        override suspend fun getEventsBetween(startMs: Long, endMs: Long): List<CalendarEvent> =
            emptyList()

        override fun hasPermission(): Boolean = granted
    }

    private fun event(
        id: Long,
        title: String,
        startOffsetMs: Long,
        durationMs: Long = 60 * 60 * 1000L,
    ) = CalendarEvent(
        id = id,
        title = title,
        description = "",
        location = "",
        startMs = now + startOffsetMs,
        endMs = now + startOffsetMs + durationMs,
        allDay = false,
        calendarName = "Work"
    )

    private fun source(provider: CalendarProvider) =
        DefaultUpcomingEventSource(provider, nowMs = { now })

    @Test
    fun `returns null when permission missing`() = runTest {
        val src = source(FakeCalendarProvider(granted = false))
        assertThat(src.nextEvent()).isNull()
    }

    @Test
    fun `returns null on provider error`() = runTest {
        val src = source(FakeCalendarProvider(error = RuntimeException("provider boom")))
        assertThat(src.nextEvent()).isNull()
    }

    @Test
    fun `returns null when there are no future events`() = runTest {
        val past = event(1, "Yesterday", startOffsetMs = -4 * 60 * 60 * 1000L)
        val src = source(FakeCalendarProvider(events = listOf(past)))
        assertThat(src.nextEvent()).isNull()
    }

    @Test
    fun `returns the earliest still-future event`() = runTest {
        val later = event(1, "Later", startOffsetMs = 5 * 60 * 60 * 1000L)
        val next = event(2, "Next", startOffsetMs = 30 * 60 * 1000L)
        val evenLater = event(3, "Even later", startOffsetMs = 8 * 60 * 60 * 1000L)
        val src = source(FakeCalendarProvider(events = listOf(later, next, evenLater)))

        val result = src.nextEvent()

        assertThat(result).isNotNull()
        assertThat(result!!.title).isEqualTo("Next")
    }

    @Test
    fun `returns a currently running event if no future one has started yet`() = runTest {
        val running = event(1, "In progress", startOffsetMs = -15 * 60 * 1000L)
        val src = source(FakeCalendarProvider(events = listOf(running)))
        val result = src.nextEvent()
        assertThat(result).isNotNull()
        assertThat(result!!.title).isEqualTo("In progress")
    }

    @Test
    fun `Empty source always returns null`() = runTest {
        assertThat(UpcomingEventSource.Empty.nextEvent()).isNull()
    }
}
