package com.opendash.app.tool.system

/**
 * Reads notifications from the device.
 * Requires BIND_NOTIFICATION_LISTENER_SERVICE permission.
 */
interface NotificationProvider {
    suspend fun listNotifications(): List<NotificationInfo>
    suspend fun clearAll(): Boolean
    suspend fun clear(packageName: String): Boolean
    fun isListenerEnabled(): Boolean

    /**
     * Send [text] as a reply to a messaging notification identified by [key].
     * Returns a [ReplyOutcome] variant describing the result so the tool
     * layer can produce a friendly spoken message.
     */
    suspend fun replyToNotification(key: String, text: String): ReplyOutcome
}

sealed interface ReplyOutcome {
    data object Sent : ReplyOutcome
    data object NotFound : ReplyOutcome
    data object NoReplyAction : ReplyOutcome
    data object ListenerNotConnected : ReplyOutcome
    data class Failed(val reason: String) : ReplyOutcome
}

data class NotificationInfo(
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAtMs: Long,
    val key: String
)
