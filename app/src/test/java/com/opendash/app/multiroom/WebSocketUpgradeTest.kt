package com.opendash.app.multiroom

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for the handshake parser + Sec-WebSocket-Accept computation.
 *
 * Fixtures include a real Chrome upgrade request so we're not just testing
 * our own code against our own code.
 */
class WebSocketUpgradeTest {

    /**
     * RFC 6455 §1.3 worked example: with client key
     * `dGhlIHNhbXBsZSBub25jZQ==` the server must respond with
     * `s3pPLMBiTxaQ9kYGzzhZRbK+xOo=`. Any hash/base64 bug shows up here.
     */
    @Test
    fun `acceptKey matches RFC 6455 sample vector`() {
        val actual = WebSocketUpgrade.acceptKey("dGhlIHNhbXBsZSBub25jZQ==")
        assertThat(actual).isEqualTo("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=")
    }

    /**
     * Parse a real Chromium browser upgrade request. We feed the first
     * line to the parser explicitly and drive the header block through
     * `nextLine`. Must extract path, key, and version.
     */
    @Test
    fun `parseUpgradeRequest accepts Chrome request`() {
        val firstLine = "GET /bus HTTP/1.1"
        val headers = arrayOf(
            "Host: 192.168.1.42:8421",
            "Connection: Upgrade",
            "Pragma: no-cache",
            "Cache-Control: no-cache",
            "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Upgrade: websocket",
            "Origin: http://192.168.1.42:8421",
            "Sec-WebSocket-Version: 13",
            "Accept-Encoding: gzip, deflate",
            "Accept-Language: en-US,en;q=0.9",
            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==",
            "Sec-WebSocket-Extensions: permessage-deflate; client_max_window_bits",
            ""
        )
        val iter = headers.iterator()
        val parsed = WebSocketUpgrade.parseUpgradeRequest(firstLine) {
            if (iter.hasNext()) iter.next() else null
        }

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.method).isEqualTo("GET")
        assertThat(parsed.path).isEqualTo("/bus")
        assertThat(parsed.key).isEqualTo("dGhlIHNhbXBsZSBub25jZQ==")
        assertThat(parsed.version).isEqualTo("13")
    }

    /**
     * Reject: request is missing `Upgrade: websocket`. Returns null so the
     * server can close the connection.
     */
    @Test
    fun `parseUpgradeRequest rejects plain GET without Upgrade header`() {
        val headers = arrayOf(
            "Host: 127.0.0.1:8421",
            "Connection: keep-alive",
            ""
        )
        val iter = headers.iterator()
        val parsed = WebSocketUpgrade.parseUpgradeRequest("GET /bus HTTP/1.1") {
            if (iter.hasNext()) iter.next() else null
        }
        assertThat(parsed).isNull()
    }

    /**
     * Reject: `Sec-WebSocket-Version` != 13. We only speak v13.
     */
    @Test
    fun `parseUpgradeRequest rejects wrong websocket version`() {
        val headers = arrayOf(
            "Host: 127.0.0.1:8421",
            "Upgrade: websocket",
            "Connection: Upgrade",
            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==",
            "Sec-WebSocket-Version: 8",
            ""
        )
        val iter = headers.iterator()
        val parsed = WebSocketUpgrade.parseUpgradeRequest("GET /bus HTTP/1.1") {
            if (iter.hasNext()) iter.next() else null
        }
        assertThat(parsed).isNull()
    }

    /**
     * Reject: first line isn't a GET request. POST / PUT with an Upgrade
     * header is still not a valid WebSocket upgrade.
     */
    @Test
    fun `parseUpgradeRequest rejects non-GET method`() {
        val headers = arrayOf(
            "Upgrade: websocket",
            "Connection: Upgrade",
            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==",
            "Sec-WebSocket-Version: 13",
            ""
        )
        val iter = headers.iterator()
        val parsed = WebSocketUpgrade.parseUpgradeRequest("POST /bus HTTP/1.1") {
            if (iter.hasNext()) iter.next() else null
        }
        assertThat(parsed).isNull()
    }

    /**
     * The 101 response is the exact bytes we write back. Must contain the
     * status line, Upgrade/Connection headers, and the computed accept
     * key followed by a blank line (CRLF-CRLF).
     */
    @Test
    fun `buildUpgradeResponse includes accept key and ends with blank line`() {
        val response = WebSocketUpgrade.buildUpgradeResponse("dGhlIHNhbXBsZSBub25jZQ==")
        assertThat(response).startsWith("HTTP/1.1 101 Switching Protocols\r\n")
        assertThat(response).contains("Upgrade: websocket\r\n")
        assertThat(response).contains("Connection: Upgrade\r\n")
        assertThat(response).contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n")
        assertThat(response).endsWith("\r\n\r\n")
    }
}
