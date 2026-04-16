package com.opensmarthome.speaker.tool.system

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * NotificationListenerService that keeps a live cache of active notifications.
 * The user must grant "Notification Access" permission in Settings.
 */
class OpenSmartSpeakerNotificationListener : NotificationListenerService() {

    companion object {
        private val cache: ConcurrentHashMap<String, StatusBarNotification> = ConcurrentHashMap()
        private var instance: OpenSmartSpeakerNotificationListener? = null

        fun getActive(): List<StatusBarNotification> = cache.values.toList()

        fun cancelAllFromListener(): Boolean {
            val inst = instance ?: return false
            return try {
                inst.cancelAllNotifications()
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to cancel all notifications")
                false
            }
        }

        fun cancelByKey(key: String): Boolean {
            val inst = instance ?: return false
            return try {
                inst.cancelNotification(key)
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to cancel notification")
                false
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        cache.clear()
        try {
            super.getActiveNotifications()?.forEach { sbn ->
                cache[sbn.key] = sbn
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to snapshot initial notifications")
        }
        Timber.d("Notification listener connected (${cache.size} active)")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        cache.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        cache[sbn.key] = sbn
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        cache.remove(sbn.key)
    }
}
