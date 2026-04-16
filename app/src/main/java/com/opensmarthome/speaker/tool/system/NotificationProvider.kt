package com.opensmarthome.speaker.tool.system

/**
 * Reads notifications from the device.
 * Requires BIND_NOTIFICATION_LISTENER_SERVICE permission.
 */
interface NotificationProvider {
    suspend fun listNotifications(): List<NotificationInfo>
    suspend fun clearAll(): Boolean
    suspend fun clear(packageName: String): Boolean
    fun isListenerEnabled(): Boolean
}

data class NotificationInfo(
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAtMs: Long,
    val key: String
)
