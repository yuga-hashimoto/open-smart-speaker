package com.opendash.app.tool.system

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * NotificationListenerService that keeps a live cache of active notifications.
 * The user must grant "Notification Access" permission in Settings.
 */
class OpenDashNotificationListener : NotificationListenerService() {

    companion object {
        private val cache: ConcurrentHashMap<String, StatusBarNotification> = ConcurrentHashMap()
        private var instance: OpenDashNotificationListener? = null

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

        /**
         * Send a text reply to a messaging notification identified by [key].
         *
         * Walks `notification.actions` looking for one whose `remoteInputs` is
         * non-empty (the "Reply" action) and fires its [PendingIntent] with the
         * supplied [text] packaged via [RemoteInput.addResultsToIntent].
         */
        fun replyToNotification(key: String, text: String): ReplyOutcome {
            val inst = instance
            if (inst == null) {
                Timber.w("replyToNotification: listener not connected")
                return ReplyOutcome.ListenerNotConnected
            }
            val sbn = cache[key]
                ?: inst.runCatching { activeNotifications?.firstOrNull { it.key == key } }
                    .getOrNull()
            if (sbn == null) {
                Timber.w("replyToNotification: no active notification for key=%s", key)
                return ReplyOutcome.NotFound
            }
            val notification = sbn.notification
            val actions: Array<Notification.Action> = notification.actions ?: emptyArray()
            val replyAction = actions.firstOrNull { action ->
                val inputs = action.remoteInputs
                inputs != null && inputs.isNotEmpty()
            }
            if (replyAction == null) {
                Timber.w(
                    "replyToNotification: no reply action on key=%s (pkg=%s)",
                    key,
                    sbn.packageName
                )
                return ReplyOutcome.NoReplyAction
            }
            val pendingIntent = replyAction.actionIntent
                ?: return ReplyOutcome.Failed("reply action has null PendingIntent")
            val remoteInputs = replyAction.remoteInputs
                ?: return ReplyOutcome.Failed("reply action has no remote inputs")
            return try {
                val intent = Intent()
                val bundle = Bundle().apply {
                    remoteInputs.forEach { input -> putCharSequence(input.resultKey, text) }
                }
                RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
                pendingIntent.send(inst, 0, intent)
                ReplyOutcome.Sent
            } catch (e: PendingIntent.CanceledException) {
                Timber.w(e, "replyToNotification: PendingIntent was cancelled")
                ReplyOutcome.Failed("pending intent cancelled")
            } catch (e: Exception) {
                Timber.e(e, "replyToNotification: failed to send reply")
                ReplyOutcome.Failed(e.message ?: "send failed")
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
