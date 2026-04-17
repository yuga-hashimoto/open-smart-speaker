package com.opensmarthome.speaker.tool.system

import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolParameter
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.tool.ToolSchema
import com.opensmarthome.speaker.util.LocaleManager
import timber.log.Timber

/**
 * Lets the LLM read and set the app's UI locale — so "switch to
 * Japanese" / "change to Español" routes through a single tool call
 * instead of requiring the user to hunt through Settings. Also exposes
 * a `list_app_locales` tool so the agent can enumerate the bundled
 * options the user is allowed to pick.
 *
 * Persist semantics mirror [LocaleManager.apply]: the empty string
 * `""` clears the override (follow the system locale). Unsupported
 * tags are rejected with a clear error — the agent can relay the
 * `available_tags` field so the user knows what to ask for.
 *
 * On Android 12 and below the platform override is unavailable; the
 * tool still persists the user's choice (so it takes effect on the
 * next system upgrade) but reports `override_supported: false` so the
 * LLM can tell the user why the UI didn't change.
 */
class LocaleToolExecutor(
    private val localeManager: LocaleManager
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "get_app_locale",
            description = "Return the currently selected UI locale as a BCP-47 tag, plus whether runtime override is supported on this device.",
            parameters = emptyMap()
        ),
        ToolSchema(
            name = "list_app_locales",
            description = "Enumerate the bundled locale options the user can switch to. Returns a list of {tag,label}.",
            parameters = emptyMap()
        ),
        ToolSchema(
            name = "set_app_locale",
            description = "Change the UI locale. Pass an empty string to follow the system locale. Rejects tags outside the bundled list — call list_app_locales to see valid options.",
            parameters = mapOf(
                "tag" to ToolParameter(
                    type = "string",
                    description = "BCP-47 tag such as 'ja', 'es', 'zh-CN', or '' for system default.",
                    required = true
                )
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult = try {
        when (call.name) {
            "get_app_locale" -> currentLocale(call)
            "list_app_locales" -> listLocales(call)
            "set_app_locale" -> setLocale(call)
            else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
        }
    } catch (e: Exception) {
        Timber.e(e, "Locale tool failed")
        ToolResult(call.id, false, "", e.message ?: "Locale tool failed")
    }

    private suspend fun currentLocale(call: ToolCall): ToolResult {
        val tag = localeManager.currentTag()
        val supported = localeManager.isOverrideSupported
        val label = localeManager.options.firstOrNull { it.tag == tag }?.label ?: "Unknown"
        val escapedTag = jsonEscape(tag)
        val escapedLabel = jsonEscape(label)
        return ToolResult(
            call.id,
            true,
            """{"tag":"$escapedTag","label":"$escapedLabel","override_supported":$supported}"""
        )
    }

    private fun listLocales(call: ToolCall): ToolResult {
        val entries = localeManager.options.joinToString(",") { opt ->
            """{"tag":"${jsonEscape(opt.tag)}","label":"${jsonEscape(opt.label)}"}"""
        }
        return ToolResult(call.id, true, """{"locales":[$entries]}""")
    }

    private suspend fun setLocale(call: ToolCall): ToolResult {
        val raw = call.arguments["tag"] as? String
            ?: return ToolResult(call.id, false, "", "Missing 'tag'")
        val tag = raw.trim()
        val valid = localeManager.options.any { it.tag == tag }
        if (!valid) {
            val availableTags = localeManager.options.joinToString(",") { "\"${jsonEscape(it.tag)}\"" }
            return ToolResult(
                call.id,
                false,
                "",
                "Unsupported locale '$tag'. Available: [$availableTags]"
            )
        }
        localeManager.apply(tag)
        val label = localeManager.options.first { it.tag == tag }.label
        val supported = localeManager.isOverrideSupported
        return ToolResult(
            call.id,
            true,
            """{"tag":"${jsonEscape(tag)}","label":"${jsonEscape(label)}","override_supported":$supported}"""
        )
    }

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}
