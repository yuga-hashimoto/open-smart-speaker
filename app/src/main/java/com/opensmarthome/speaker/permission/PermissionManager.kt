package com.opensmarthome.speaker.permission

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Queries the current permission state for every catalog entry.
 * Non-UI class so it can be used from ViewModels or directly from tools.
 */
class PermissionManager(
    private val context: Context,
    private val notificationListenerClass: Class<*>?,
    private val accessibilityServiceClass: Class<*>?
) {

    data class State(
        val entries: List<EntryState>,
        val specialGrants: List<SpecialGrantState>
    ) {
        val anyMissing: Boolean
            get() = entries.any { !it.granted && !it.entry.optional } ||
                specialGrants.any { !it.granted && false /* special grants are optional */ }
    }

    data class EntryState(val entry: PermissionCatalog.Entry, val granted: Boolean)
    data class SpecialGrantState(val grant: PermissionCatalog.SpecialGrant, val granted: Boolean)

    fun snapshot(): State {
        val entries = PermissionCatalog.entries().map { entry ->
            val applicable = android.os.Build.VERSION.SDK_INT >= entry.requireSdk
            val granted = if (applicable) {
                ContextCompat.checkSelfPermission(context, entry.permission) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
            EntryState(entry, granted)
        }
        val specials = PermissionCatalog.specialGrants.map { grant ->
            val granted = when (grant.id) {
                "notification_listener" -> isNotificationListenerEnabled()
                "accessibility" -> isAccessibilityEnabled()
                else -> false
            }
            SpecialGrantState(grant, granted)
        }
        return State(entries, specials)
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val cls = notificationListenerClass ?: return false
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val cn = ComponentName(context, cls)
        return enabled.split(":").any { it == cn.flattenToString() }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val cls = accessibilityServiceClass ?: return false
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(context, cls)
        return enabled.split(":").any { it == cn.flattenToString() }
    }
}
