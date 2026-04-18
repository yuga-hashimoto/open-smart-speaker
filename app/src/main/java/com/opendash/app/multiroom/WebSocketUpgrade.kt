package com.opendash.app.multiroom

import java.security.MessageDigest
import java.util.Base64

/**
 * RFC 6455 handshake helpers for the multi-room WebSocket server.
 *
 * The server in [AnnouncementServer] peeks the first line of every inbound
 * connection. If it looks like an HTTP request (`GET /bus HTTP/1.1`), the
 * full request header block is parsed with [parseUpgradeRequest] and, if
 * the `Upgrade: websocket` + `Sec-WebSocket-Key` headers are present,
 * [acceptKey] produces the value we echo back in
 * `Sec-WebSocket-Accept:`. Any failure returns `null`; the server then
 * rejects the connection by closing it.
 *
 * Kept deliberately small — we don't validate `Sec-WebSocket-Version`
 * beyond "present and == 13" and we don't negotiate extensions or
 * sub-protocols, matching the ADR's "v1 dumb WS" scope.
 */
internal object WebSocketUpgrade {

    /** RFC 6455 magic GUID used in the Sec-WebSocket-Accept computation. */
    private const val WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    /**
     * Parsed result of an HTTP upgrade request. `key` is the raw value of
     * the `Sec-WebSocket-Key` header — we echo its `acceptKey()` back in
     * the 101 response.
     */
    data class UpgradeRequest(
        val method: String,
        val path: String,
        val key: String,
        val version: String
    )

    /**
     * Parse an HTTP/1.1 upgrade request header block. [firstLine] is the
     * already-consumed request line (e.g. `GET /bus HTTP/1.1`); the header
     * continuation is read line-by-line from [nextLine] until a blank line.
     *
     * Returns the parsed upgrade request on success, `null` when:
     * - the first line isn't well-formed
     * - the `Upgrade` header isn't `websocket` (case-insensitive)
     * - `Connection` header doesn't contain `upgrade`
     * - `Sec-WebSocket-Key` is missing
     * - `Sec-WebSocket-Version` isn't `13`
     *
     * Headers with the same name are concatenated with ", " per RFC 7230
     * rules, which is enough for our purposes (we only read `Connection`
     * and compare case-insensitively).
     */
    fun parseUpgradeRequest(firstLine: String, nextLine: () -> String?): UpgradeRequest? {
        val parts = firstLine.trim().split(" ")
        if (parts.size < 3) return null
        val method = parts[0]
        val path = parts[1]
        val proto = parts[2]
        if (!method.equals("GET", ignoreCase = true)) return null
        if (!proto.startsWith("HTTP/1.", ignoreCase = true)) return null

        val headers = mutableMapOf<String, String>()
        while (true) {
            val raw = nextLine() ?: return null
            val line = raw.trimEnd('\r')
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            val prev = headers[name]
            headers[name] = if (prev.isNullOrEmpty()) value else "$prev, $value"
        }

        val upgrade = headers["upgrade"] ?: return null
        if (!upgrade.equals("websocket", ignoreCase = true)) return null
        val connection = headers["connection"] ?: return null
        if (!connection.lowercase().split(",").map { it.trim() }.contains("upgrade")) return null
        val key = headers["sec-websocket-key"]?.takeIf { it.isNotBlank() } ?: return null
        val version = headers["sec-websocket-version"] ?: return null
        if (version.trim() != "13") return null

        return UpgradeRequest(method = method, path = path, key = key, version = version.trim())
    }

    /**
     * Compute the `Sec-WebSocket-Accept` value for a given client
     * `Sec-WebSocket-Key`:
     *
     * `base64( sha1( key + WS_GUID ) )`
     *
     * Uses Android's `Base64` so the output matches what real browser
     * clients expect (NO_WRAP flag to avoid trailing newlines).
     */
    fun acceptKey(key: String): String {
        val sha1 = MessageDigest.getInstance("SHA-1")
        val digest = sha1.digest((key + WS_GUID).toByteArray(Charsets.US_ASCII))
        return Base64.getEncoder().encodeToString(digest)
    }

    /**
     * The exact HTTP/1.1 response sent after a successful upgrade. Uses
     * CRLF line endings per RFC 2616 §4.1.
     */
    fun buildUpgradeResponse(key: String): String {
        val accept = acceptKey(key)
        return buildString {
            append("HTTP/1.1 101 Switching Protocols\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Accept: ").append(accept).append("\r\n")
            append("\r\n")
        }
    }
}
