package com.opensmarthome.speaker.multiroom

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

class AnnouncementClientTest {

    private val client = AnnouncementClient()

    /**
     * Happy path: the client writes `HELLO ndjson\n` followed by the envelope
     * line, then closes. We assert the on-wire byte layout.
     */
    @Test
    fun `send writes HELLO preamble and line then closes`() = runBlocking {
        // Use an in-process ServerSocket bound to a random free port on
        // loopback. Accept one connection and capture the raw bytes until EOF.
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val received = AtomicReference<String?>(null)
        val acceptJob = async(Dispatchers.IO) {
            server.accept().use { sock ->
                val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
                // Greedy read until EOF so we see both the HELLO and the line.
                received.set(reader.readText())
            }
        }

        val outcome = withTimeout(5_000) {
            client.send(
                host = server.inetAddress.hostAddress ?: "127.0.0.1",
                port = server.localPort,
                line = """{"v":1,"type":"tts_broadcast"}"""
            )
        }
        awaitAll(acceptJob)
        server.close()

        assertThat(outcome).isEqualTo(SendOutcome.Ok)
        val body = received.get() ?: ""
        // Format must start with the exact HELLO sentinel and include the envelope
        // line + newline. This is what AnnouncementServer.handleClient expects.
        assertThat(body).startsWith("HELLO ndjson\n")
        assertThat(body).endsWith("""{"v":1,"type":"tts_broadcast"}""" + "\n")
    }

    /**
     * Connection refused: pick a free port, close the socket, try to reach it.
     * Loopback rejects the SYN immediately with RST → [ConnectException].
     */
    @Test
    fun `send reports ConnectionRefused when peer port is closed`() = runBlocking {
        val port = withContext(Dispatchers.IO) {
            val s = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
            val p = s.localPort
            s.close()
            p
        }

        val outcome = client.send(
            host = InetAddress.getLoopbackAddress().hostAddress ?: "127.0.0.1",
            port = port,
            line = """{"v":1}"""
        )
        assertThat(outcome).isEqualTo(SendOutcome.ConnectionRefused)
    }

    /**
     * Invalid host: unresolvable name → generic [SendOutcome.Other]. Uses the
     * reserved RFC 2606 test TLD so no real DNS lookup could ever succeed.
     */
    @Test
    fun `send reports Other on unresolvable host`() = runTest {
        val outcome = client.send(
            host = "no-such-host.invalid",
            port = 1,
            line = "{}",
            timeoutMs = 1_000L
        )
        assertThat(outcome).isInstanceOf(SendOutcome.Other::class.java)
    }
}
