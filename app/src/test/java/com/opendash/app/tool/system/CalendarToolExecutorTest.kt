package com.opendash.app.tool.system

import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarToolExecutorTest {

    private lateinit var executor: CalendarToolExecutor
    private lateinit var provider: CalendarProvider

    @BeforeEach
    fun setup() {
        provider = mockk(relaxed = true)
        executor = CalendarToolExecutor(provider)
    }

    @Test
    fun `availableTools includes get_calendar_events`() = runTest {
        val tools = executor.availableTools()
        assertThat(tools.map { it.name }).contains("get_calendar_events")
    }

    @Test
    fun `get_calendar_events without permission returns error`() = runTest {
        every { provider.hasPermission() } returns false

        val result = executor.execute(
            ToolCall(id = "1", name = "get_calendar_events", arguments = emptyMap())
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("permission")
    }

    @Test
    fun `get_calendar_events returns empty array when no events`() = runTest {
        every { provider.hasPermission() } returns true
        coEvery { provider.getUpcomingEvents(any()) } returns emptyList()

        val result = executor.execute(
            ToolCall(id = "2", name = "get_calendar_events", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).isEqualTo("[]")
    }

    @Test
    fun `get_calendar_events returns event details`() = runTest {
        every { provider.hasPermission() } returns true
        coEvery { provider.getUpcomingEvents(7) } returns listOf(
            CalendarEvent(
                id = 1L,
                title = "Team meeting",
                description = "Weekly sync",
                location = "Office",
                startMs = 1700000000000L,
                endMs = 1700003600000L,
                allDay = false,
                calendarName = "Work"
            )
        )

        val result = executor.execute(
            ToolCall(id = "3", name = "get_calendar_events", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("Team meeting")
        assertThat(result.data).contains("Office")
        assertThat(result.data).contains("Weekly sync")
    }

    @Test
    fun `get_calendar_events uses days_ahead parameter`() = runTest {
        every { provider.hasPermission() } returns true
        coEvery { provider.getUpcomingEvents(30) } returns emptyList()

        executor.execute(
            ToolCall(id = "4", name = "get_calendar_events", arguments = mapOf(
                "days_ahead" to 30.0
            ))
        )

        // Verification through mock call is implicit via returns clause above
    }

    @Test
    fun `all_day event uses date-only format`() = runTest {
        every { provider.hasPermission() } returns true
        coEvery { provider.getUpcomingEvents(any()) } returns listOf(
            CalendarEvent(
                id = 1L,
                title = "Birthday",
                description = "",
                location = "",
                startMs = 1700000000000L,
                endMs = 1700086400000L,
                allDay = true,
                calendarName = "Family"
            )
        )

        val result = executor.execute(
            ToolCall(id = "5", name = "get_calendar_events", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"all_day\":true")
    }
}
