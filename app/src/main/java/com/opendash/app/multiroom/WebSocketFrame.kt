package com.opendash.app.multiroom

import java.io.InputStream
import java.io.OutputStream

/**
 * Minimal RFC 6455 frame encoder/decoder for the multi-room WebSocket bus.
 *
 * **Intentional scope (v1):**
 * - Only final, unfragmented **text** frames (opcode 0x1) are read as real
 *   payloads. All other opcodes (binary, ping, pong, control) decode to
 *   [Read.Unsupported] or [Read.Close] and are quietly handled by the caller.
 * - Server-side writes emit unmasked text frames; per RFC 6455, a server
 *   MUST NOT mask frames it sends, so [writeTextFrame] never sets the mask
 *   bit.
 * - Supported payload sizes: 0..65 535 bytes via the 7-bit or 16-bit
 *   extended length path. 64-bit extended length (>= 65 536) is
 *   deliberately rejected because a single [AnnouncementEnvelope] never
 *   approaches that size — preventing memory DoS from a malformed frame
 *   is more important than forward compatibility.
 *
 * Keeping this hand-rolled (no Ktor, no Java-WebSocket lib) satisfies the
 * "no new dependencies" constraint from the multi-room ADR. The surface is
 * small enough to review and unit-test exhaustively.
 */
internal object WebSocketFrame {

    /**
     * Result of reading a single frame off an [InputStream].
     */
    sealed interface Read {
        /** Final text frame, fully unmasked and UTF-8 decoded. */
        data class Text(val text: String) : Read
        /** Peer sent a CLOSE control frame; caller should stop the loop. */
        data object Close : Read
        /** Binary / ping / pong / continuation — caller drops or ignores. */
        data object Unsupported : Read
    }

    private const val MAX_FRAME_LENGTH = 65_535

    /**
     * Read one WebSocket frame from [input]. Returns `null` on end-of-stream
     * (connection closed before a full frame header arrived).
     *
     * Masking key is consumed and applied to the payload before returning.
     * Per RFC 6455, **client frames MUST be masked** — we therefore require
     * the mask bit in practice, but we tolerate unmasked frames for test
     * fixtures and hypothetical server-origin traffic.
     */
    fun readFrame(input: InputStream): Read? {
        val b0 = input.read().takeIf { it >= 0 } ?: return null
        val b1 = input.read().takeIf { it >= 0 } ?: return null

        val opcode = b0 and 0x0F
        val masked = (b1 and 0x80) != 0
        val len7 = b1 and 0x7F

        val payloadLen: Int = when {
            len7 < 126 -> len7
            len7 == 126 -> {
                val h = input.read().takeIf { it >= 0 } ?: return null
                val l = input.read().takeIf { it >= 0 } ?: return null
                (h shl 8) or l
            }
            // len7 == 127 → 64-bit extended length. Rejected by design.
            else -> return Read.Unsupported
        }

        if (payloadLen < 0 || payloadLen > MAX_FRAME_LENGTH) return Read.Unsupported

        val mask = if (masked) readExact(input, 4) ?: return null else null
        val payload = if (payloadLen == 0) ByteArray(0) else readExact(input, payloadLen) ?: return null
        if (mask != null) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            }
        }

        return when (opcode) {
            0x1 -> Read.Text(String(payload, Charsets.UTF_8))
            0x8 -> Read.Close
            else -> Read.Unsupported   // binary (0x2), ping (0x9), pong (0xA), continuation (0x0)
        }
    }

    /**
     * Write a single final, unmasked text frame for [text] to [output] and
     * flush. Caller keeps ownership of the stream.
     *
     * Uses the 7-bit length form when possible; falls back to the 16-bit
     * extended form for payloads in 126..65 535 bytes. Payloads larger
     * than [MAX_FRAME_LENGTH] throw [IllegalArgumentException] — the
     * caller should never attempt it for NDJSON envelopes.
     */
    fun writeTextFrame(output: OutputStream, text: String) {
        val payload = text.toByteArray(Charsets.UTF_8)
        require(payload.size <= MAX_FRAME_LENGTH) {
            "WebSocketFrame payload too large: ${payload.size} bytes (max $MAX_FRAME_LENGTH)"
        }
        output.write(0x81)              // fin=1, opcode=0x1 (text)
        when {
            payload.size < 126 -> output.write(payload.size and 0x7F)
            else -> {
                output.write(126)
                output.write((payload.size ushr 8) and 0xFF)
                output.write(payload.size and 0xFF)
            }
        }
        output.write(payload)
        output.flush()
    }

    /**
     * Read exactly [n] bytes or return `null` on premature EOF.
     * Handles the case where the underlying stream delivers data in chunks.
     */
    private fun readExact(input: InputStream, n: Int): ByteArray? {
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = input.read(buf, read, n - read)
            if (r < 0) return null
            read += r
        }
        return buf
    }
}
