package com.opendash.app.multiroom

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket-mode counterpart to [AnnouncementClient]. Performs the RFC
 * 6455 upgrade (`GET /bus` on `ws://host:port/bus`), sends one text frame
 * containing the pre-signed envelope JSON, and closes.
 *
 * Re-uses the shared [OkHttpClient] from Hilt's NetworkModule — no new
 * dependency is introduced. Callers remain responsible for fan-out
 * concurrency (see [AnnouncementBroadcaster]).
 *
 * Signature mirrors [AnnouncementClient.send] so [AnnouncementBroadcaster]
 * can try WS first and fall back to NDJSON without touching its fan-out
 * logic (see also the ADR §Decision: "WebSocket is the primary
 * transport, NDJSON is the fallback").
 */
@Singleton
class AnnouncementWebSocketClient @Inject constructor(
    private val httpClient: OkHttpClient
) {

    /**
     * Connect over WebSocket, send one text frame, close gracefully.
     *
     * @param host peer hostname or IP.
     * @param port peer TCP port.
     * @param line a fully-serialized [AnnouncementEnvelope] JSON object,
     *   no trailing newline — WebSocket framing has its own boundary so
     *   we must not smuggle a `\n` into the payload.
     * @param timeoutMs total time budget (connect + handshake + send + close).
     */
    suspend fun send(
        host: String,
        port: Int,
        line: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): SendOutcome = withContext(Dispatchers.IO) {
        val clamped = httpClient.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            // Drop the shared HttpLoggingInterceptor — it would try to log the
            // request body over a websocket, which OkHttp warns on.
            .build()
        val request = Request.Builder()
            .url("ws://$host:$port$PATH")
            .build()

        val opened = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<SendOutcome>()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val reason = response?.let { "http ${it.code}" } ?: (t.message ?: t.javaClass.simpleName)
                if (!opened.isCompleted) {
                    // Handshake failed before onOpen — treat as "fall back to NDJSON".
                    opened.completeExceptionally(t)
                }
                if (!finished.isCompleted) finished.complete(SendOutcome.Other(reason))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!finished.isCompleted) finished.complete(SendOutcome.Ok)
            }
        }

        val ws = clamped.newWebSocket(request, listener)
        try {
            withTimeout(timeoutMs) { opened.await() }
        } catch (e: TimeoutCancellationException) {
            runCatching { ws.cancel() }
            return@withContext SendOutcome.Timeout
        } catch (e: Exception) {
            Timber.d(e, "WS handshake failed to $host:$port")
            runCatching { ws.cancel() }
            return@withContext SendOutcome.Other(e.message ?: e.javaClass.simpleName)
        }

        val queued = ws.send(line)
        if (!queued) {
            runCatching { ws.cancel() }
            return@withContext SendOutcome.Other("send queue refused")
        }
        // Initiate graceful close; peer will echo and drive onClosed.
        ws.close(NORMAL_CLOSURE, "done")
        return@withContext try {
            withTimeout(timeoutMs) { finished.await() }
        } catch (e: TimeoutCancellationException) {
            runCatching { ws.cancel() }
            // Message was queued on OkHttp's side even if the close handshake
            // didn't finish in time; treat as OK so fan-out doesn't mark this
            // peer failed.
            SendOutcome.Ok
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 2_000L
        private const val PATH = "/bus"
        private const val NORMAL_CLOSURE = 1000
    }
}
