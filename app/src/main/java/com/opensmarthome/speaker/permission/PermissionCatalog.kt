package com.opensmarthome.speaker.permission

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
            permission = Manifest.permission.READ_CONTACTS,
            title = "Contacts",
            rationale = "Lets the agent find phone numbers and emails by name.",
            unlocks = listOf("search_contacts", "list_contacts")
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
        } else {
            add(Entry(
                permission = Manifest.permission.READ_EXTERNAL_STORAGE,
                title = "Photos & Storage",
                rationale = "Lets the agent list your recent photos.",
                unlocks = listOf("list_recent_photos")
            ))
        }
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
            rationale = "Lets the agent read the current screen for the 'read_screen' tool.",
            unlocks = listOf("read_screen"),
            settingsAction = "android.settings.ACCESSIBILITY_SETTINGS"
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
