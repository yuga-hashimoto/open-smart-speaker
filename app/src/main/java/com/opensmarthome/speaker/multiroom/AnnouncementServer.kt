package com.opensmarthome.speaker.multiroom

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-room message-bus server. Listens on
 * [MulticastDiscovery.DEFAULT_PORT] by default. Each inbound connection
 * is sniffed on its first line and routed to one of three code paths:
 *
 * 1. **WebSocket** (`GET /bus HTTP/1.1` + `Upgrade: websocket`): after
 *    the RFC 6455 handshake completes, each client text frame becomes
 *    one NDJSON envelope for [AnnouncementParser]. Binary/ping/pong
 *    frames are dropped (v1 scope).
 * 2. **NDJSON with sentinel** (`HELLO ndjson`): subsequent lines are
 *    envelopes. The sentinel is how NDJSON-first clients
 *    ([AnnouncementClient]) announce themselves so that a mistaken
 *    WebSocket bind attempt in the future can't accidentally swallow
 *    them.
 * 3. **Raw NDJSON** (anything else): legacy / `nc`-for-debugging path.
 *    Treat the first line as an envelope and keep reading.
 *
 * Server is opt-in via `MULTIROOM_BROADCAST_ENABLED`; [VoiceService]
 * manages [start]/[stop].
 */
@Singleton
class AnnouncementServer @Inject constructor(
    private val parser: AnnouncementParser,
    private val dispatcher: AnnouncementDispatcher,
    private val rejectionRecorder: MultiroomRejectionRecorder? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var acceptJob: Job? = null
    @Volatile private var serverSocket: ServerSocket? = null

    /**
     * Start listening on [port]. Idempotent — calling twice without [stop] is
     * a no-op and the existing server continues.
     */
    fun start(port: Int = com.opensmarthome.speaker.util.MulticastDiscovery.DEFAULT_PORT) {
        if (acceptJob != null) return
        try {
            serverSocket = ServerSocket(port)
            Timber.d("AnnouncementServer listening on $port")
        } catch (e: Exception) {
            Timber.w(e, "AnnouncementServer: failed to bind port $port")
            serverSocket = null
            return
        }
        acceptJob = scope.launch {
            val sock = serverSocket ?: return@launch
            while (isActiveAndOpen(sock)) {
                val client = try { sock.accept() } catch (_: Exception) { break }
                launch { handleClient(client) }
            }
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    internal fun handleClient(socket: Socket) {
        socket.use { s ->
            try {
                val input = s.getInputStream()
                val first = readLineFromStream(input) ?: return

                if (looksLikeHttpRequest(first)) {
                    handleWebSocketUpgrade(
                        firstLine = first,
                        rawInput = input,
                        output = s.getOutputStream()
                    )
                    return
                }

                // Accept both a HELLO sentinel and direct NDJSON — the sentinel
                // is optional for legacy clients. Treat "HELLO ndjson" as a no-op
                // prelude. Once we're in NDJSON mode we can safely buffer for
                // efficient line reads.
                val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                val firstPayload = if (first.trim().equals("HELLO ndjson", ignoreCase = true)) {
                    reader.readLine() ?: return
                } else first
                processLine(firstPayload)
                while (true) {
                    val line = reader.readLine() ?: break
                    processLine(line)
                }
            } catch (e: Exception) {
                Timber.d("AnnouncementServer client loop exited: ${e.message}")
            }
        }
    }

    /**
     * Read a CRLF- or LF-terminated line directly off [input] without
     * buffering ahead. Used for the initial sniff so that post-handshake
     * WebSocket frame bytes aren't accidentally consumed by a
     * [BufferedReader]. UTF-8 safe for ASCII-only header lines (which is
     * all the HTTP/1.1 request line and headers use).
     *
     * Returns null at end-of-stream before any byte was read.
     */
    private fun readLineFromStream(input: InputStream): String? {
        val buf = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.isEmpty()) null else buf.toString()
            if (b == '\n'.code) {
                // Strip trailing \r if present (CRLF).
                if (buf.isNotEmpty() && buf.last() == '\r') buf.deleteCharAt(buf.length - 1)
                return buf.toString()
            }
            buf.append(b.toChar())
        }
    }

    private fun looksLikeHttpRequest(line: String): Boolean {
        // HTTP request lines always end with "HTTP/x.y". We restrict to GET for
        // our path so a random POST-with-body doesn't get mistaken for one.
        val trimmed = line.trim()
        return trimmed.startsWith("GET ", ignoreCase = true) &&
            trimmed.contains("HTTP/1.", ignoreCase = true)
    }

    /**
     * Complete an RFC 6455 handshake and pump inbound text frames into the
     * parser. Any failure (missing headers, stream close, unsupported frame)
     * exits the loop silently — we don't want to help unknown clients
     * enumerate the server's capabilities.
     *
     * After the 101 response the raw input stream is used directly because
     * [BufferedReader] would mangle binary frame bytes; however, since the
     * reader already consumed the handshake lines and nothing more, the
     * underlying stream's cursor is positioned at the first frame byte.
     */
    private fun handleWebSocketUpgrade(
        firstLine: String,
        rawInput: InputStream,
        output: OutputStream
    ) {
        val upgrade = WebSocketUpgrade.parseUpgradeRequest(firstLine) { readLineFromStream(rawInput) }
        if (upgrade == null) {
            Timber.d("WebSocket upgrade rejected: malformed handshake")
            return
        }
        val response = WebSocketUpgrade.buildUpgradeResponse(upgrade.key)
        runCatching {
            output.write(response.toByteArray(Charsets.US_ASCII))
            output.flush()
        }.onFailure {
            Timber.d(it, "WebSocket upgrade response write failed")
            return
        }

        // Switch to binary framing. Because the handshake was read byte-by-
        // byte (readLineFromStream) without look-ahead buffering, the raw
        // InputStream cursor sits exactly at the first frame byte.
        while (true) {
            val frame = try {
                WebSocketFrame.readFrame(rawInput) ?: break
            } catch (e: Exception) {
                Timber.d(e, "WebSocket frame read failed")
                break
            }
            when (frame) {
                is WebSocketFrame.Read.Text -> processLine(frame.text)
                is WebSocketFrame.Read.Close -> break
                is WebSocketFrame.Read.Unsupported -> {
                    // Binary / ping / pong — drop silently for v1.
                }
            }
        }
    }

    private fun processLine(line: String) {
        if (line.isBlank()) return
        when (val r = parser.parse(line)) {
            is AnnouncementParser.ParseResult.Ok -> dispatcher.dispatch(r.envelope)
            is AnnouncementParser.ParseResult.Rejected -> {
                Timber.d("Envelope rejected: ${r.reason} ${r.detail}")
                rejectionRecorder?.record(reason = r.reason.name)
            }
        }
    }

    private fun isActiveAndOpen(sock: ServerSocket): Boolean =
        !sock.isClosed && acceptJob?.isActive == true
}
