package com.opensmarthome.speaker.tool

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ToolFilterTest {

    private val all = listOf(
        ToolSchema("set_timer", "Timer", emptyMap()),
        ToolSchema("execute_command", "Devices", emptyMap()),
        ToolSchema("get_weather", "Weather", emptyMap()),
        ToolSchema("set_volume", "Volume", emptyMap()),
        ToolSchema("get_calendar_events", "Cal", emptyMap()),
        ToolSchema("take_photo", "Photo", emptyMap()),
        ToolSchema("get_datetime", "Now", emptyMap()),
        ToolSchema("remember", "Memory", emptyMap())
    )

    @Test
    fun `no keyword match returns full list`() {
        val filtered = ToolFilter.filterByIntent(all, "tell me a joke about elephants")
        assertThat(filtered).hasSize(all.size)
    }

    @Test
    fun `weather keyword keeps weather + always-on tools`() {
        val filtered = ToolFilter.filterByIntent(all, "what's the weather today")
        val names = filtered.map { it.name }
        assertThat(names).contains("get_weather")
        assertThat(names).contains("get_datetime") // always-on
        assertThat(names).contains("remember") // always-on
        assertThat(names).doesNotContain("take_photo")
    }

    @Test
    fun `timer keyword exposes timer tools only`() {
        val filtered = ToolFilter.filterByIntent(all, "set a timer for 5 minutes")
        val names = filtered.map { it.name }
        assertThat(names).contains("set_timer")
        assertThat(names).doesNotContain("get_calendar_events")
    }

    @Test
    fun `japanese music keyword expands media tools`() {
        val filtered = ToolFilter.filterByIntent(all, "音楽を再生して")
        val names = filtered.map { it.name }
        assertThat(names).contains("execute_command")
        assertThat(names).contains("set_volume")
    }
}
