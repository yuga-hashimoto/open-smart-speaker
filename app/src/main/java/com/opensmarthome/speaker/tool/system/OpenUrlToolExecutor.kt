package com.opensmarthome.speaker.tool.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolParameter
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.tool.ToolSchema
import timber.log.Timber
import java.net.URI
import java.net.URISyntaxException

/**
 * Opens an http/https URL in the user's default browser via `Intent.ACTION_VIEW`.
 *
 * **Safety:** only `http://` and `https://` schemes are accepted. Every
 * other scheme — `javascript:`, `intent://`, `content://`, `file://`, etc.
 * — is refused because they can escalate into code execution, private
 * data exfiltration, or local file access.
 *
 * Validation pipeline:
 * 1. Parse with [URI] to catch malformed inputs.
 * 2. Scheme check against an allow-list of `http` / `https`.
 * 3. Dispatch `Intent(ACTION_VIEW, Uri.parse(url))` with `FLAG_ACTIVITY_NEW_TASK`.
 *
 * `intentFactory` is injected so unit tests can capture the dispatched URL
 * without Robolectric. Production defaults to the real `Intent` constructor.
 */
class OpenUrlToolExecutor(
    private val context: Context,
    /**
     * Builds the `Intent` for the given URL. The default creates
     * `Intent(ACTION_VIEW, Uri.parse(url))`. Tests inject a lambda that
     * returns a MockK-managed Intent.
     */
    private val intentFactory: (String) -> Intent = { url ->
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "open_url",
            description = "Open an http/https URL in the default browser. " +
                "Only http:// and https:// schemes are accepted.",
            parameters = mapOf(
                "url" to ToolParameter(
                    type = "string",
                    description = "Absolute http or https URL (e.g. https://example.com).",
                    required = true
                )
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "open_url" -> executeOpen(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "open_url failed")
            ToolResult(call.id, false, "", e.message ?: "Open URL failed")
        }
    }

    private fun executeOpen(call: ToolCall): ToolResult {
        val raw = call.arguments["url"] as? String
            ?: return ToolResult(call.id, false, "", "Missing url parameter")
        val url = raw.trim()
        if (url.isEmpty()) {
            return ToolResult(call.id, false, "", "Missing url parameter")
        }

        val parsed: URI = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return ToolResult(call.id, false, "", "Malformed URL: ${e.message}")
        }

        val scheme = parsed.scheme?.lowercase()
        if (scheme !in ALLOWED_SCHEMES) {
            return ToolResult(
                call.id,
                false,
                "",
                "Only http/https URLs are allowed for safety. Got: $url"
            )
        }

        val host = parsed.host?.takeIf { it.isNotEmpty() }
            ?: return ToolResult(call.id, false, "", "Malformed URL: missing host")

        val intent = intentFactory(url).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        Timber.d("Opened URL: $url")

        val spoken = "Opening $host."
        return ToolResult(
            call.id,
            true,
            """{"url":"$url","host":"$host","spoken":"$spoken"}"""
        )
    }

    companion object {
        val ALLOWED_SCHEMES: Set<String> = setOf("http", "https")

        /** English "Opening <domain>." confirmation. */
        fun spokenEn(host: String): String = "Opening $host."

        /** Japanese "<domain>を開きました。" confirmation. */
        fun spokenJa(host: String): String = "${host}を開きました。"
    }
}
