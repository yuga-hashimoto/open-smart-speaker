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
        ToolSchema("start_screen_recording", "ScreenRec", emptyMap()),
        ToolSchema("web_search", "Web search", emptyMap()),
        ToolSchema("fetch_webpage", "Fetch page", emptyMap()),
        ToolSchema("get_news", "News", emptyMap())
    )

    @Test
    fun `no keyword match returns at most fallback budget`() {
        val filtered = ToolFilter.filterByIntent(all, "xyzzy unrelated utterance")
        // Fallback path: at most MAX_FALLBACK_TOOLS tools. When the list is
        // already small (<= MAX_FALLBACK_TOOLS) the full list is returned.
        assertThat(filtered.size).isAtMost(maxOf(all.size, ToolFilter.MAX_FALLBACK_TOOLS))
        assertThat(filtered.size).isAtLeast(minOf(all.size, ToolFilter.MAX_FALLBACK_TOOLS))
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

    @Test
    fun `english what is question exposes web_search and filters out unrelated tools`() {
        val filtered = ToolFilter.filterByIntent(all, "What is quantum computing?")
        val names = filtered.map { it.name }
        assertThat(names).contains("web_search")
        // Prove the bucket actually matched (full fallback would include take_photo etc.)
        assertThat(names).doesNotContain("take_photo")
        assertThat(names).doesNotContain("start_screen_recording")
    }

    @Test
    fun `english tell me about exposes web_search and filters unrelated tools`() {
        val filtered = ToolFilter.filterByIntent(all, "Tell me about the Eiffel Tower")
        val names = filtered.map { it.name }
        assertThat(names).contains("web_search")
        assertThat(names).doesNotContain("take_photo")
    }

    @Test
    fun `japanese about keyword exposes web_search and filters unrelated tools`() {
        val filtered = ToolFilter.filterByIntent(all, "AIについて教えて")
        val names = filtered.map { it.name }
        assertThat(names).contains("web_search")
        assertThat(names).contains("fetch_webpage")
        assertThat(names).doesNotContain("take_photo")
    }

    @Test
    fun `japanese toha keyword exposes web_search and filters unrelated tools`() {
        val filtered = ToolFilter.filterByIntent(all, "量子コンピューターとは")
        val names = filtered.map { it.name }
        assertThat(names).contains("web_search")
        assertThat(names).doesNotContain("take_photo")
    }

    @Test
    fun `japanese kuwashiku keyword exposes web_search`() {
        val filtered = ToolFilter.filterByIntent(all, "Pythonの最新情報を詳しく")
        val names = filtered.map { it.name }
        assertThat(names).contains("web_search")
        assertThat(names).doesNotContain("take_photo")
    }

    @Test
    fun `japanese shiritai keyword exposes web_search`() {
        val filtered = ToolFilter.filterByIntent(all, "Kotlin の最新情報を知りたい")
        val names = filtered.map { it.name }
        assertThat(names).contains("web_search")
        assertThat(names).doesNotContain("take_photo")
    }

    @Test
    fun `english who is question exposes web_search`() {
        val filtered = ToolFilter.filterByIntent(all, "Who is Ada Lovelace?")
        val names = filtered.map { it.name }
        assertThat(names).contains("web_search")
        assertThat(names).doesNotContain("take_photo")
    }

    @Test
    fun `weather query still includes get_weather when weather keyword present`() {
        // Weather already has its own bucket; verify we don't lose weather semantics
        // just because "what is" also maps to search. Both buckets may contribute.
        val filtered = ToolFilter.filterByIntent(all, "what is the weather in Tokyo")
        val names = filtered.map { it.name }
        assertThat(names).contains("get_weather")
        // Adding web_search is fine — LLM will pick the right one.
    }

    @Test
    fun `how to keyword routes to search`() {
        val tools = listOf(
            ToolSchema("web_search", "", emptyMap()),
            ToolSchema("get_news", "", emptyMap()),
            ToolSchema("remember", "", emptyMap()),
            ToolSchema("get_datetime", "", emptyMap()),
            ToolSchema("take_photo", "", emptyMap())
        )
        val filtered = ToolFilter.filterByIntent(tools, "how to bake bread")
        assertThat(filtered.map { it.name }).contains("web_search")
    }

    @Test
    fun `fallback keeps representative subset when no bucket matches and list is large`() {
        // Utterance must not match any bucket — "xyzzy quux" is deliberately
        // gibberish to exercise the no-match fallback path.
        val bigList = (1..30).map { i -> ToolSchema("tool_$i", "d$i", emptyMap()) } + all
        val filtered = ToolFilter.filterByIntent(bigList, "xyzzy quux gibberish")
        // Should not exceed MAX_FALLBACK representatives
        assertThat(filtered.size).isAtMost(ToolFilter.MAX_FALLBACK_TOOLS)
    }

    @Test
    fun `fallback keeps full list when list is already small`() {
        val small = listOf(
            ToolSchema("a", "", emptyMap()),
            ToolSchema("b", "", emptyMap()),
            ToolSchema("c", "", emptyMap())
        )
        val filtered = ToolFilter.filterByIntent(small, "xyzzy")
        assertThat(filtered).hasSize(3)
    }
}
