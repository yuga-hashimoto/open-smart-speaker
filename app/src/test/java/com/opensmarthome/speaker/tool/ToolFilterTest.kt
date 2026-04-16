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
        ToolSchema("remember", "Memory", emptyMap()),
        ToolSchema("get_location", "Location", emptyMap()),
        ToolSchema("find_device", "FindDevice", emptyMap()),
        ToolSchema("send_sms", "Sms", emptyMap()),
        ToolSchema("search_contacts", "Contacts", emptyMap()),
        ToolSchema("ingest_document", "Doc", emptyMap()),
        ToolSchema("retrieve_document", "Doc", emptyMap()),
        ToolSchema("start_screen_recording", "ScreenRec", emptyMap())
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

    @Test
    fun `where am i exposes get_location`() {
        val filtered = ToolFilter.filterByIntent(all, "where am i right now")
        assertThat(filtered.map { it.name }).contains("get_location")
    }

    @Test
    fun `find my tablet exposes find_device`() {
        val filtered = ToolFilter.filterByIntent(all, "find my tablet")
        assertThat(filtered.map { it.name }).contains("find_device")
    }

    @Test
    fun `send a message exposes sms and contacts`() {
        val filtered = ToolFilter.filterByIntent(all, "send a message to mom")
        val names = filtered.map { it.name }
        assertThat(names).contains("send_sms")
        assertThat(names).contains("search_contacts")
    }

    @Test
    fun `ingest document exposes rag tools`() {
        val filtered = ToolFilter.filterByIntent(all, "ingest this document")
        val names = filtered.map { it.name }
        assertThat(names).contains("ingest_document")
        assertThat(names).contains("retrieve_document")
    }

    @Test
    fun `japanese current location keyword`() {
        val filtered = ToolFilter.filterByIntent(all, "現在地を教えて")
        assertThat(filtered.map { it.name }).contains("get_location")
    }
}
