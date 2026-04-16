package com.opensmarthome.speaker.tool.info

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.Test

class RssNewsProviderTest {

    private val provider = RssNewsProvider(client = mockk())

    @Test
    fun `parses RSS 2-0 feed`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
<channel>
  <item>
    <title>First story</title>
    <description>Short summary.</description>
    <link>https://example.com/1</link>
    <pubDate>Mon, 16 Apr 2026 10:00:00 GMT</pubDate>
  </item>
  <item>
    <title>Second story</title>
    <description>Another summary.</description>
    <link>https://example.com/2</link>
    <pubDate>Mon, 16 Apr 2026 11:00:00 GMT</pubDate>
  </item>
</channel>
</rss>"""

        val items = provider.parseFeed(xml, limit = 10)

        assertThat(items).hasSize(2)
        assertThat(items[0].title).isEqualTo("First story")
        assertThat(items[0].summary).contains("Short summary")
        assertThat(items[0].link).isEqualTo("https://example.com/1")
    }

    @Test
    fun `parses Atom feed`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <entry>
    <title>Atom story</title>
    <summary>Atom summary.</summary>
    <link href="https://example.com/atom/1" />
    <updated>2026-04-16T10:00:00Z</updated>
  </entry>
</feed>"""

        val items = provider.parseFeed(xml, limit = 10)

        assertThat(items).hasSize(1)
        assertThat(items[0].title).isEqualTo("Atom story")
        assertThat(items[0].link).isEqualTo("https://example.com/atom/1")
    }

    @Test
    fun `respects limit`() {
        val xml = """<?xml version="1.0"?>
<rss version="2.0"><channel>
  <item><title>A</title><link>http://a</link></item>
  <item><title>B</title><link>http://b</link></item>
  <item><title>C</title><link>http://c</link></item>
</channel></rss>"""

        val items = provider.parseFeed(xml, limit = 2)

        assertThat(items).hasSize(2)
    }

    @Test
    fun `returns empty on malformed feed`() {
        val items = provider.parseFeed("<not-a-feed>", limit = 5)
        assertThat(items).isEmpty()
    }
}
