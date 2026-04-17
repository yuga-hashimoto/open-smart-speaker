package com.opensmarthome.speaker.multiroom

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Behavioural tests for [AnnouncementServer.handleClient]: confirms that
 * the first-line sniff routes to NDJSON vs WebSocket correctly and that
 * a WebSocket text frame is unmasked and forwarded to the parser just like
 * an NDJSON line.
 *
 * These tests deliberately use the real `handleClient` entry point rather
 * than a mocked socket — we want the whole sniff + upgrade + frame path
 * exercised end-to-end. Sockets are in-process loopback pairs.
 */
class AnnouncementServerTest {

    @Test
    fun `HELLO ndjson routes to NDJSON parser`() = runBlocking {
        val (parser, dispatcher, server) = newServer()
        val capturedLine = slot<String>()
        every { parser.parse(capture(capturedLine)) } returns stubReject()

        val (clientSide, serverSide) = loopbackPair()

        val handle = async(Dispatchers.IO) { server.handleClient(serverSide) }

        clientSide.getOutputStream().apply {
            write("HELLO ndjson\n".toByteArray())
            write("""{"v":1,"type":"tts_broadcast"}""".toByteArray())
            write("\n".toByteArray())
            flush()
        }
        // Signal EOF so the server loop exits.
        clientSide.shutdownOutput()

        withTimeout(5_000) { handle.await() }

        assertThat(capturedLine.captured).isEqualTo("""{"v":1,"type":"tts_broadcast"}""")
        verify(exactly = 0) { dispatcher.dispatch(any()) }
        clientSide.close()
    }

    @Test
    fun `HMAC_MISMATCH rejection is recorded once with reason name`() = runBlocking {
        val parser = mockk<AnnouncementParser>()
        val dispatcher = mockk<AnnouncementDispatcher>(relaxed = true)
        val recorder = mockk<MultiroomRejectionRecorder>(relaxed = true)
        val server = AnnouncementServer(parser, dispatcher, recorder)

        every { parser.parse(any()) } returns AnnouncementParser.ParseResult.Rejected(
            reason = AnnouncementParser.Reason.HMAC_MISMATCH,
            detail = "bad signature"
        )

        val (clientSide, serverSide) = loopbackPair()
        val handle = async(Dispatchers.IO) { server.handleClient(serverSide) }

        clientSide.getOutputStream().apply {
            write("""{"v":1,"type":"tts_broadcast"}""".toByteArray())
            write("\n".toByteArray())
            flush()
        }
        clientSide.shutdownOutput()
        withTimeout(5_000) { handle.await() }

        verify(exactly = 1) { recorder.record(reason = "HMAC_MISMATCH", nowMs = any()) }
        verify(exactly = 0) { dispatcher.dispatch(any()) }
        clientSide.close()
    }

    @Test
    fun `null rejection recorder does not crash processLine`() = runBlocking {
        // Existing production wiring defaults to null — a rejection must
        // be a no-op rather than throwing when the recorder is absent.
        val parser = mockk<AnnouncementParser>()
        val dispatcher = mockk<AnnouncementDispatcher>(relaxed = true)
        val server = AnnouncementServer(parser, dispatcher, rejectionRecorder = null)

        every { parser.parse(any()) } returns AnnouncementParser.ParseResult.Rejected(
            reason = AnnouncementParser.Reason.MALFORMED_JSON,
            detail = "unit test"
        )

        val (clientSide, serverSide) = loopbackPair()
        val handle = async(Dispatchers.IO) { server.handleClient(serverSide) }

        clientSide.getOutputStream().apply {
            write("garbage\n".toByteArray())
            flush()
        }
        clientSide.shutdownOutput()
        withTimeout(5_000) { handle.await() }

        verify(exactly = 0) { dispatcher.dispatch(any()) }
        clientSide.close()
    }

    @Test
    fun `raw NDJSON without HELLO routes to NDJSON parser`() = runBlocking {
        val (parser, _, server) = newServer()
        val captured = slot<String>()
        every { parser.parse(capture(captured)) } returns stubReject()

        val (clientSide, serverSide) = loopbackPair()

        val handle = async(Dispatchers.IO) { server.handleClient(serverSide) }

        clientSide.getOutputStream().apply {
            write("""{"v":1,"type":"heartbeat"}""".toByteArray())
            write("\n".toByteArray())
            flush()
        }
        clientSide.shutdownOutput()
        withTimeout(5_000) { handle.await() }

        assertThat(captured.captured).isEqualTo("""{"v":1,"type":"heartbeat"}""")
        clientSide.close()
    }

    @Test
    fun `GET bus HTTP upgrade completes handshake and routes frame to parser`() = runBlocking {
        val (parser, _, server) = newServer()
        val captured = slot<String>()
        every { parser.parse(capture(captured)) } returns stubReject()

        val (clientSide, serverSide) = loopbackPair()

        val handle = async(Dispatchers.IO) { server.handleClient(serverSide) }

        // Write the upgrade request byte-for-byte.
        val out = clientSide.getOutputStream()
        val upgradeReq = buildString {
            append("GET /bus HTTP/1.1\r\n")
            append("Host: 127.0.0.1:8421\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n")
            append("Sec-WebSocket-Version: 13\r\n")
            append("\r\n")
        }
        out.write(upgradeReq.toByteArray(Charsets.US_ASCII))
        out.flush()

        // Read and assert the 101 response.
        val response = readHttpResponse(clientSide)
        assertThat(response).contains("101 Switching Protocols")
        assertThat(response).contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=")

        // Build a real masked client text frame containing an envelope.
        val envelope = """{"v":1,"type":"tts_broadcast"}"""
        val frame = buildMaskedClientTextFrame(envelope)
        out.write(frame)
        out.flush()
        // Close → server loop exits.
        clientSide.shutdownOutput()
        withTimeout(5_000) { handle.await() }

        assertThat(captured.captured).isEqualTo(envelope)
        clientSide.close()
    }

    private fun newServer(): Triple<AnnouncementParser, AnnouncementDispatcher, AnnouncementServer> {
        val parser = mockk<AnnouncementParser>()
        val dispatcher = mockk<AnnouncementDispatcher>(relaxed = true)
        return Triple(parser, dispatcher, AnnouncementServer(parser, dispatcher))
    }

    private fun stubReject() = AnnouncementParser.ParseResult.Rejected(
        reason = AnnouncementParser.Reason.MALFORMED_JSON,
        detail = "unit test"
    )

    private fun loopbackPair(): Pair<Socket, Socket> {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val client = Socket(server.inetAddress, server.localPort)
        val accepted = server.accept()
        server.close()
        return client to accepted
    }

    private fun readHttpResponse(socket: Socket): String {
        // Read exactly until CRLF-CRLF so we don't consume any trailing
        // frame bytes. Byte-level state machine.
        val input = socket.getInputStream()
        val buf = StringBuilder()
        var matched = 0
        val target = "\r\n\r\n"
        while (matched < target.length) {
            val b = input.read()
            if (b < 0) break
            buf.append(b.toChar())
            if (b.toChar() == target[matched]) matched++ else {
                // Reset — allow possible restart with current char.
                matched = if (b.toChar() == target[0]) 1 else 0
            }
        }
        return buf.toString()
    }

    /**
     * Build a single final, masked text frame for [text]. Mirrors exactly
     * what a Chrome / OkHttp client would send.
     */
    private fun buildMaskedClientTextFrame(text: String): ByteArray {
        val payload = text.toByteArray(Charsets.UTF_8)
        val mask = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val out = ByteArrayOutputStream()
        out.write(0x81)
        if (payload.size < 126) {
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
        return out.toByteArray()
    }
}
