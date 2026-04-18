package com.opendash.app.tool.system

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

/**
 * Deep-links into Android system Settings screens via standard Intents.
 *
 * No root, no accessibility — just PackageManager-resolvable `Settings.ACTION_*`
 * intents. Each request is launched with `FLAG_ACTIVITY_NEW_TASK` because the
 * executor is invoked from an app-process context that is not an Activity.
 *
 * The `page` argument is a short slug (enum) mapped to the actual Intent
 * action. See [slugToAction] for the supported set.
 */
class OpenSettingsToolExecutor(
    private val context: Context,
    /**
     * Intent factory — defaults to the real [Intent] constructor. Tests can
     * inject a lambda that returns a MockK-managed [Intent] so the executor
     * stays unit-testable without Robolectric.
     */
    private val intentFactory: (String) -> Intent = { action -> Intent(action) }
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "open_settings_page",
            description = "Open an Android system Settings screen. " +
                "Supported pages: wifi, bluetooth, display, brightness, sound, volume, " +
                "accessibility, notifications, apps, battery, home.",
            parameters = mapOf(
                "page" to ToolParameter(
                    type = "string",
                    description = "Settings page slug (e.g. wifi, bluetooth, display).",
                    required = true,
                    enum = SUPPORTED_PAGES
                )
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "open_settings_page" -> executeOpen(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "open_settings_page failed")
            ToolResult(call.id, false, "", e.message ?: "Settings open failed")
        }
    }

    private fun executeOpen(call: ToolCall): ToolResult {
        val pageRaw = call.arguments["page"] as? String
            ?: return ToolResult(call.id, false, "", "Missing page parameter")
        val page = pageRaw.trim().lowercase()
        val action = slugToAction(page)
            ?: return ToolResult(call.id, false, "", "Unknown settings page: $page")

        val intent = intentFactory(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        Timber.d("Opened settings page: $page ($action)")
        val spoken = spokenConfirmation(page)
        return ToolResult(
            call.id,
            true,
            """{"page":"$page","action":"$action","spoken":"$spoken"}"""
        )
    }

    companion object {
        val SUPPORTED_PAGES: List<String> = listOf(
            "wifi",
            "bluetooth",
            "display",
            "brightness",
            "sound",
            "volume",
            "accessibility",
            "notifications",
            "apps",
            "battery",
            "home",
            "main"
        )

        fun slugToAction(page: String): String? = when (page) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "notifications" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "home", "main" -> Settings.ACTION_SETTINGS
            else -> null
        }

        private fun spokenConfirmation(page: String): String = when (page) {
            "wifi" -> "Opening Wi-Fi settings."
            "bluetooth" -> "Opening Bluetooth settings."
            "display", "brightness" -> "Opening display settings."
            "sound", "volume" -> "Opening sound settings."
            "accessibility" -> "Opening accessibility settings."
            "notifications" -> "Opening notification settings."
            "apps" -> "Opening app settings."
            "battery" -> "Opening battery settings."
            "home", "main" -> "Opening settings."
            else -> "Opening settings."
        }
    }
}
