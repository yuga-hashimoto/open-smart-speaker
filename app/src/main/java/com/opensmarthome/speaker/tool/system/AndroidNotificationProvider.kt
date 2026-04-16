package com.opensmarthome.speaker.tool.system

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * Android implementation of NotificationProvider.
 * Uses OpenSmartSpeakerNotificationListener (requires user permission).
 */
class AndroidNotificationProvider(
    private val context: Context
) : NotificationProvider {

    override suspend fun listNotifications(): List<NotificationInfo> {
        if (!isListenerEnabled()) return emptyList()

        val pm = context.packageManager
        return OpenSmartSpeakerNotificationListener.getActive().map { sbn ->
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
            } catch (e: Exception) {
                sbn.packageName
            }
            val extras = sbn.notification.extras
            val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()

            NotificationInfo(
                packageName = sbn.packageName,
                appName = appName,
                title = title,
                text = text,
                postedAtMs = sbn.postTime,
                key = sbn.key
            )
        }
    }

    override suspend fun clearAll(): Boolean =
        OpenSmartSpeakerNotificationListener.cancelAllFromListener()

    override suspend fun clear(packageName: String): Boolean {
        val matching = OpenSmartSpeakerNotificationListener.getActive()
            .filter { it.packageName == packageName }
        if (matching.isEmpty()) return false
        var any = false
        for (sbn in matching) {
            if (OpenSmartSpeakerNotificationListener.cancelByKey(sbn.key)) {
                any = true
            }
        }
        return any
    }

    override fun isListenerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val cn = ComponentName(context, OpenSmartSpeakerNotificationListener::class.java)
        return enabled.split(":").any { it == cn.flattenToString() }
    }
}
