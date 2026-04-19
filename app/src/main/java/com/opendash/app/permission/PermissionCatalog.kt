package com.opendash.app.permission

import android.Manifest
import android.os.Build

/**
 * Catalogs all permissions the agent's tools need, with user-facing rationale.
 * Powers the onboarding flow and a "what permissions does this need" Settings page.
 */
object PermissionCatalog {

    data class Entry(
        val permission: String,
        val title: String,
        val rationale: String,
        val unlocks: List<String>, // tool names enabled by this grant
        val optional: Boolean = true,
        val requireSdk: Int = 0
    )

    fun entries(): List<Entry> = buildList {
        add(Entry(
            permission = Manifest.permission.RECORD_AUDIO,
            title = "Microphone",
            rationale = "Needed to hear your voice commands and wake word.",
            unlocks = listOf("voice_input"),
            optional = false
        ))
        add(Entry(
            permission = Manifest.permission.READ_CALENDAR,
            title = "Calendar",
            rationale = "Lets the agent read your upcoming events.",
            unlocks = listOf("get_calendar_events")
        ))
        add(Entry(
            permission = Manifest.permission.WRITE_CALENDAR,
            title = "Calendar (write)",
            rationale = "Lets the agent add new events when you ask.",
            unlocks = listOf("create_calendar_event")
        ))
        add(Entry(
            permission = Manifest.permission.READ_CONTACTS,
            title = "Contacts",
            rationale = "Lets the agent find phone numbers and emails by name.",
            unlocks = listOf("search_contacts", "list_contacts")
        ))
        add(Entry(
            permission = Manifest.permission.WRITE_CONTACTS,
            title = "Contacts (write)",
            rationale = "Lets the agent save new contacts you dictate.",
            unlocks = listOf("add_contact")
        ))
        add(Entry(
            permission = Manifest.permission.READ_PHONE_STATE,
            title = "Phone state",
            rationale = "Lets the agent know when a call is ringing or in progress so it can pause the wake word.",
            unlocks = listOf("device_health")
        ))
        add(Entry(
            permission = Manifest.permission.ACCESS_COARSE_LOCATION,
            title = "Location",
            rationale = "Lets the agent answer 'where am I?' and provide weather for your area.",
            unlocks = listOf("get_location")
        ))
        add(Entry(
            permission = Manifest.permission.SEND_SMS,
            title = "SMS",
            rationale = "Lets the agent send text messages on your behalf (always with confirmation).",
            unlocks = listOf("send_sms")
        ))
        add(Entry(
            permission = Manifest.permission.CAMERA,
            title = "Camera",
            rationale = "Lets the agent take photos when you ask.",
            unlocks = listOf("take_photo")
        ))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Entry(
                permission = Manifest.permission.READ_MEDIA_IMAGES,
                title = "Photos",
                rationale = "Lets the agent list your recent photos.",
                unlocks = listOf("list_recent_photos"),
                requireSdk = Build.VERSION_CODES.TIRAMISU
            ))
            add(Entry(
                permission = Manifest.permission.READ_MEDIA_AUDIO,
                title = "Music",
                rationale = "Lets the agent list and play your music library.",
                unlocks = listOf("list_recent_audio"),
                requireSdk = Build.VERSION_CODES.TIRAMISU
            ))
            add(Entry(
                permission = Manifest.permission.READ_MEDIA_VIDEO,
                title = "Videos",
                rationale = "Lets the agent list and play your videos.",
                unlocks = listOf("list_recent_videos"),
                requireSdk = Build.VERSION_CODES.TIRAMISU
            ))
        } else {
            add(Entry(
                permission = Manifest.permission.READ_EXTERNAL_STORAGE,
                title = "Photos, Music & Videos",
                rationale = "Lets the agent list your recent media.",
                unlocks = listOf("list_recent_photos", "list_recent_audio", "list_recent_videos")
            ))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Entry(
                permission = Manifest.permission.BLUETOOTH_CONNECT,
                title = "Bluetooth",
                rationale = "Lets the agent see paired Bluetooth speakers, earbuds, and watches.",
                unlocks = listOf("list_bluetooth_devices"),
                requireSdk = Build.VERSION_CODES.S
            ))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Entry(
                permission = Manifest.permission.ACTIVITY_RECOGNITION,
                title = "Activity",
                rationale = "Lets proactive suggestions know if you're walking or driving.",
                unlocks = listOf("activity_recognition"),
                requireSdk = Build.VERSION_CODES.Q
            ))
        }
        add(Entry(
            permission = Manifest.permission.BODY_SENSORS,
            title = "Body sensors",
            rationale = "Lets the agent read heart-rate from a connected wearable.",
            unlocks = listOf("device_health")
        ))
    }

    /** Special permissions that aren't granted via runtime request (Settings dialog). */
    val specialGrants: List<SpecialGrant> = listOf(
        SpecialGrant(
            id = "notification_listener",
            title = "Notification Access",
            rationale = "Lets the agent read your notifications to answer 'what did I miss?'.",
            unlocks = listOf("list_notifications", "clear_notifications"),
            settingsAction = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
        ),
        SpecialGrant(
            id = "accessibility",
            title = "Accessibility",
            rationale = "Lets the agent read what's on screen and tap buttons on your behalf when you ask. No data leaves the device; you can turn it off anytime in Settings > Accessibility.",
            unlocks = listOf(
                "read_screen",
                "read_active_screen",
                "tap_by_text",
                "scroll_screen",
                "type_text"
            ),
            settingsAction = "android.settings.ACCESSIBILITY_SETTINGS"
        ),
        SpecialGrant(
            id = "overlay",
            title = "Display over other apps",
            rationale = "Lets the floating voice orb stay on top of any app so you can talk to OpenDash without leaving what you're doing.",
            unlocks = listOf("voice_overlay"),
            settingsAction = "android.settings.action.MANAGE_OVERLAY_PERMISSION"
        ),
        SpecialGrant(
            id = "write_settings",
            title = "Modify system settings",
            rationale = "Lets the agent change brightness, screen-off timeout, and ringer mode when you ask.",
            unlocks = listOf("set_brightness", "set_screen_timeout"),
            settingsAction = "android.settings.action.MANAGE_WRITE_SETTINGS"
        ),
        SpecialGrant(
            id = "usage_stats",
            title = "Usage access",
            rationale = "Lets the agent answer 'how much screen time today?' and surface app usage trends.",
            unlocks = listOf("get_app_usage_stats"),
            settingsAction = "android.settings.USAGE_ACCESS_SETTINGS"
        ),
        SpecialGrant(
            id = "battery_optimization",
            title = "Ignore battery optimisations",
            rationale = "Keeps the always-on voice service running so wake-word detection isn't paused by Doze.",
            unlocks = listOf("voice_input"),
            settingsAction = "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
        )
    )

    data class SpecialGrant(
        val id: String,
        val title: String,
        val rationale: String,
        val unlocks: List<String>,
        val settingsAction: String
    )
}
