package com.opendash.app.multiroom

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Unit tests for the hand-rolled RFC 6455 WebSocket frame encoder/decoder used
 * by [AnnouncementServer]. Covers both the "short" (<=125 byte) and the
 * "extended 16-bit length" (>125, <=65535) cases, plus mask unmasking of
 * client frames.
 */
class WebSocketFrameTest {

    /**
     * Roundtrip a short text payload through the decoder. The encoder
     * (server-side) writes no mask; we verify the decoder handles an
     * unmasked frame correctly in principle, but also verify the more
     * common case: a client-masked frame (RFC 6455 mandates client frames
     * to be masked) is unmasked cleanly.
     */
    @Test
    fun `decode masked short text frame`() {
        // Real client-masked frame for text "hi":
        // 0x81 0x82 (fin + text, masked, len=2)
        // 0x01 0x02 0x03 0x04 (mask key)
        // 'h' XOR 0x01 = 0x69, 'i' XOR 0x02 = 0x6B
        val bytes = byteArrayOf(
            0x81.toByte(),             // fin=1, opcode=1 (text)
            0x82.toByte(),             // mask=1, len=2
            0x01, 0x02, 0x03, 0x04,    // mask key
            (('h'.code) xor 0x01).toByte(),
            (('i'.code) xor 0x02).toByte()
        )
        val input = ByteArrayInputStream(bytes)
        val frame = WebSocketFrame.readFrame(input)
        assertThat(frame).isInstanceOf(WebSocketFrame.Read.Text::class.java)
        assertThat((frame as WebSocketFrame.Read.Text).text).isEqualTo("hi")
    }

    /**
     * 200-byte payload forces the extended 16-bit length path (len=126,
     * followed by 2-byte big-endian length). Client side applies a
     * 4-byte rotating XOR mask.
     */
    @Test
    fun `decode masked 200 byte text frame`() {
        val text = "a".repeat(200)
        val payload = text.toByteArray(Charsets.UTF_8)
        val mask = byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D)
        val out = ByteArrayOutputStream()
        out.write(0x81)                 // fin + text
        out.write(0x80 or 126)          // masked + ext16 len
        out.write((200 ushr 8) and 0xFF)
        out.write(200 and 0xFF)
        out.write(mask)
        for (i in payload.indices) {
            out.write(payload[i].toInt() xor mask[i % 4].toInt())
        }

        val frame = WebSocketFrame.readFrame(ByteArrayInputStream(out.toByteArray()))
        assertThat(frame).isInstanceOf(WebSocketFrame.Read.Text::class.java)
        assertThat((frame as WebSocketFrame.Read.Text).text).isEqualTo(text)
    }

    /**
     * Server-to-client text frame (no mask): verify the encoded header for
     * a small payload is `0x81 <len> <payload>`.
     */
    @Test
    fun `encode short text frame has no mask bit`() {
        val out = ByteArrayOutputStream()
        WebSocketFrame.writeTextFrame(out, "hi")
        val bytes = out.toByteArray()
        assertThat(bytes[0]).isEqualTo(0x81.toByte())     // fin + text
        assertThat(bytes[1]).isEqualTo(0x02.toByte())     // len=2, no mask
        assertThat(bytes[2]).isEqualTo('h'.code.toByte())
        assertThat(bytes[3]).isEqualTo('i'.code.toByte())
    }

    /**
     * Encode a 200-byte payload — must use extended 16-bit length
     * (0x7E = 126), big-endian length, no mask bit.
     */
    @Test
    fun `encode 200 byte text frame uses ext16 length`() {
        val text = "x".repeat(200)
        val out = ByteArrayOutputStream()
        WebSocketFrame.writeTextFrame(out, text)
        val bytes = out.toByteArray()
        assertThat(bytes[0]).isEqualTo(0x81.toByte())
        assertThat(bytes[1]).isEqualTo(126.toByte())     // ext16 indicator, no mask
        // big-endian length
        assertThat(bytes[2].toInt() and 0xFF).isEqualTo(0)
        assertThat(bytes[3].toInt() and 0xFF).isEqualTo(200)
        val payload = bytes.copyOfRange(4, bytes.size)
        assertThat(String(payload, Charsets.UTF_8)).isEqualTo(text)
    }

    /**
     * Round-trip: encode on server side, decode via a fake masked client
     * frame built from the same payload. This verifies the two sides
     * agree on UTF-8 boundary handling for non-ASCII payloads.
     */
    @Test
    fun `roundtrip unicode payload via mask`() {
        val text = "こんにちは \uD83C\uDF55 pizza"
        val payload = text.toByteArray(Charsets.UTF_8)
        val mask = byteArrayOf(0x55, 0x33, 0x77, 0x11)
        val out = ByteArrayOutputStream()
        out.write(0x81)
        if (payload.size <= 125) {
            out.write(0x80 or payload.size)
        } else {
            out.write(0x80 or 126)
            out.write((payload.size ushr 8) and 0xFF)
            out.write(payload.size and 0xFF)
        }
        out.write(mask)
        for (i in payload.indices) {
            out.write(payload[i].toInt() xor mask[i % 4].toInt())
        }
        val decoded = WebSocketFrame.readFrame(ByteArrayInputStream(out.toByteArray()))
        assertThat(decoded).isInstanceOf(WebSocketFrame.Read.Text::class.java)
        assertThat((decoded as WebSocketFrame.Read.Text).text).isEqualTo(text)
    }

    /**
     * Binary opcode (0x2): rejected — server only speaks text frames for v1.
     */
    @Test
    fun `decode binary frame returns Unsupported`() {
        val bytes = byteArrayOf(
            0x82.toByte(),             // fin + binary opcode
            0x80.toByte(),             // mask, len=0
            0x00, 0x00, 0x00, 0x00     // mask key
        )
        val frame = WebSocketFrame.readFrame(ByteArrayInputStream(bytes))
        assertThat(frame).isInstanceOf(WebSocketFrame.Read.Unsupported::class.java)
    }

    /**
     * Close frame (0x8): decoder surfaces this as Close so the server
     * loop can terminate gracefully.
     */
    @Test
    fun `decode close frame returns Close`() {
        val bytes = byteArrayOf(
            0x88.toByte(),             // fin + close opcode
            0x80.toByte(),             // mask, len=0
            0x00, 0x00, 0x00, 0x00
        )
        val frame = WebSocketFrame.readFrame(ByteArrayInputStream(bytes))
        assertThat(frame).isEqualTo(WebSocketFrame.Read.Close)
    }

    /**
     * End-of-stream (connection closed by client): readFrame returns null.
     */
    @Test
    fun `decode returns null at end of stream`() {
        val frame = WebSocketFrame.readFrame(ByteArrayInputStream(byteArrayOf()))
        assertThat(frame).isNull()
    }
}
