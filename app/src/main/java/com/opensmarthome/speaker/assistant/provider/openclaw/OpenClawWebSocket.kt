package com.opensmarthome.speaker.assistant.provider.openclaw

import com.squareup.moshi.Moshi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class OpenClawWebSocket(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val config: OpenClawConfig
) {
    private var webSocket: WebSocket? = null
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    private val isConnected = AtomicBoolean(false)
    private val reconnectAttempts = AtomicInteger(0)

    val messages: Flow<String> = messageChannel.receiveAsFlow()

    fun connect() {
        val request = Request.Builder()
            .url(config.gatewayUrl)
            .apply {
                if (config.apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer ${config.apiKey}")
                }
            }
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.set(true)
                reconnectAttempts.set(0)
                Timber.d("OpenClaw WebSocket connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                messageChannel.trySend(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Timber.d("OpenClaw WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected.set(false)
                Timber.d("OpenClaw WebSocket closed: $code $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected.set(false)
                Timber.e(t, "OpenClaw WebSocket failure")
                scheduleReconnect()
            }
        })
    }

    fun send(message: String): Boolean {
        return webSocket?.send(message) ?: false
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isConnected.set(false)
    }

    fun isConnected(): Boolean = isConnected.get()

    private fun scheduleReconnect() {
        val attempts = reconnectAttempts.incrementAndGet()
        if (attempts <= config.maxReconnectAttempts) {
            Timber.d("Scheduling reconnect attempt $attempts/${config.maxReconnectAttempts}")
            // Reconnect logic delegated to caller via connection state observation
        }
    }
}
