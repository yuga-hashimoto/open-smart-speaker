package com.opendash.app.tool.info

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.Test

class HtmlWebFetcherTest {

    private val fetcher = HtmlWebFetcher(client = mockk())

    @Test
    fun `extractTitle returns title content`() {
        val html = "<html><head><title>Hello World</title></head><body>x</body></html>"
        assertThat(fetcher.extractTitle(html)).isEqualTo("Hello World")
    }

    @Test
    fun `extractTitle decodes entities`() {
        val html = "<html><title>Foo &amp; Bar</title></html>"
        assertThat(fetcher.extractTitle(html)).isEqualTo("Foo & Bar")
    }

    @Test
    fun `stripHtml removes scripts styles and tags`() {
        val html = """<html>
            <head><style>body{color:red}</style><script>alert('x')</script></head>
            <body><p>Hello <b>world</b></p><!-- comment --></body>
        </html>"""

        val text = fetcher.stripHtml(html)

        assertThat(text).contains("Hello")
        assertThat(text).contains("world")
        assertThat(text).doesNotContain("color:red")
        assertThat(text).doesNotContain("alert")
        assertThat(text).doesNotContain("comment")
        assertThat(text).doesNotContain("<")
    }

    @Test
    fun `stripHtml collapses whitespace`() {
        val html = "<p>a</p>\n\n\n<p>b</p>   <p>c</p>"
        val text = fetcher.stripHtml(html)
        assertThat(text).isEqualTo("a b c")
    }

    @Test
    fun `entity decoding handles common cases`() {
        val html = "<p>&quot;hello&quot; &#39;world&#39; &nbsp;space</p>"
        val text = fetcher.stripHtml(html)
        assertThat(text).contains("\"hello\"")
        assertThat(text).contains("'world'")
    }
}
