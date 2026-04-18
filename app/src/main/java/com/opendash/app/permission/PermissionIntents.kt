package com.opendash.app.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Intents that open the OS settings pages required for special grants
 * (notification listener, accessibility service, write system settings, etc).
 *
 * Used by the Settings screen to let the user deep-link to the right page
 * without manually navigating through Android's multi-level menus.
 */
object PermissionIntents {

    /** Opens the app-specific details page — useful when permissions were denied. */
    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Opens Notification Access settings (for NotificationListenerService enablement). */
    fun notificationListenerSettings(): Intent =
        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Opens Accessibility settings. */
    fun accessibilitySettings(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Opens the voice-input settings page (for choosing default assistant). */
    fun voiceInputSettings(): Intent =
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Generic deep-link by settings action string — used for PermissionCatalog SpecialGrant. */
    fun byAction(action: String): Intent =
        Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
}
