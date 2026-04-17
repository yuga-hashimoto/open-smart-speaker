package com.opensmarthome.speaker.multiroom

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-peer NDJSON writer for the multi-room message bus. Opens a TCP
 * socket, sends `HELLO ndjson\n` + one envelope line, and closes.
 *
 * Pairs with [AnnouncementServer] (receiver). WebSocket upgrade is
 * explicitly deferred — NDJSON is the documented fallback in
 * `docs/multi-room-protocol.md` and keeps this dependency-free.
 *
 * Every [send] runs on [Dispatchers.IO]. Callers remain responsible for
 * fan-out concurrency (see [AnnouncementBroadcaster]).
 */
@Singleton
class AnnouncementClient @Inject constructor() {

    /**
     * Send a single pre-signed NDJSON line to [host]:[port].
     *
     * @param line a fully-serialized [AnnouncementEnvelope] JSON object, no
     *   trailing newline — one is added by this method.
     * @param timeoutMs connect + write timeout, default 2 seconds.
     */
    suspend fun send(
        host: String,
        port: Int,
        line: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): SendOutcome = withContext(Dispatchers.IO) {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), timeoutMs.toInt())
            socket.soTimeout = timeoutMs.toInt()
            val out = socket.getOutputStream()
            out.write(PREAMBLE)
            out.write(line.toByteArray(Charsets.UTF_8))
            out.write(NEWLINE)
            out.flush()
            SendOutcome.Ok
        } catch (e: SocketTimeoutException) {
            Timber.d("AnnouncementClient timeout to $host:$port")
            SendOutcome.Timeout
        } catch (e: ConnectException) {
            Timber.d("AnnouncementClient connection refused $host:$port")
            SendOutcome.ConnectionRefused
        } catch (e: IOException) {
            Timber.d(e, "AnnouncementClient I/O failure to $host:$port")
            SendOutcome.Other(e.message ?: e.javaClass.simpleName)
        } catch (e: Exception) {
            Timber.d(e, "AnnouncementClient unexpected failure to $host:$port")
            SendOutcome.Other(e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { socket.close() }
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 2_000L
        private val PREAMBLE = "HELLO ndjson\n".toByteArray(Charsets.UTF_8)
        private val NEWLINE = "\n".toByteArray(Charsets.UTF_8)
    }
}

/** Outcome of a single-peer send. */
sealed interface SendOutcome {
    data object Ok : SendOutcome
    data object ConnectionRefused : SendOutcome
    data object Timeout : SendOutcome
    data class Other(val reason: String) : SendOutcome
}
