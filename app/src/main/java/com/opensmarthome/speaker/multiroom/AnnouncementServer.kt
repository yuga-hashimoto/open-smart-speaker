package com.opensmarthome.speaker.multiroom

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal NDJSON TCP server for the multi-room message bus. Listens on
 * [MulticastDiscovery.DEFAULT_PORT] by default; each line is one
 * [AnnouncementEnvelope] JSON. Replaces the "no server listens yet" gap
 * from the P14.5 ADR.
 *
 * WebSocket upgrade is deliberately deferred to a follow-up PR — NDJSON is
 * explicitly the documented fallback (see `docs/multi-room-protocol.md`,
 * "HELLO ndjson"). Landing NDJSON first keeps this PR dependency-free;
 * adding the WebSocket handshake would pull in Ktor or nano-httpd.
 *
 * Server is opt-in via MULTIROOM_BROADCAST_ENABLED preference; the
 * VoiceService lifecycle runs [start] / [stop].
 */
@Singleton
class AnnouncementServer @Inject constructor(
    private val parser: AnnouncementParser,
    private val dispatcher: AnnouncementDispatcher
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
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val first = reader.readLine() ?: return
                // Accept both a HELLO sentinel and direct NDJSON — the sentinel is
                // optional for the current implementation because we don't do
                // WebSocket upgrade yet. Treat "HELLO ndjson" as a no-op prelude.
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

    private fun processLine(line: String) {
        if (line.isBlank()) return
        when (val r = parser.parse(line)) {
            is AnnouncementParser.ParseResult.Ok -> dispatcher.dispatch(r.envelope)
            is AnnouncementParser.ParseResult.Rejected -> {
                Timber.d("Envelope rejected: ${r.reason} ${r.detail}")
            }
        }
    }

    private fun isActiveAndOpen(sock: ServerSocket): Boolean =
        !sock.isClosed && acceptJob?.isActive == true
}
