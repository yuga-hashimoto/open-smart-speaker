package com.opensmarthome.speaker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.opensmarthome.speaker.data.preferences.PreferenceKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber

private val Context.mediaButtonDataStore: androidx.datastore.core.DataStore<Preferences> by preferencesDataStore(name = "settings")

class MediaButtonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return

        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        if (event.action != KeyEvent.ACTION_DOWN) return

        // Check MEDIA_BUTTON_ENABLED preference — skip if disabled
        val enabled = try {
            runBlocking {
                context.mediaButtonDataStore.data.first()[PreferenceKeys.MEDIA_BUTTON_ENABLED]
            } ?: false // Default off (opt-in)
        } catch (e: Exception) {
            Timber.w(e, "Failed to read MEDIA_BUTTON_ENABLED, defaulting to off")
            false
        }

        if (!enabled) {
            Timber.d("Media button pressed but disabled via preference")
            return
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                Timber.d("Media button pressed, triggering voice input")
                val serviceIntent = Intent(context, VoiceService::class.java).apply {
                    action = VoiceService.ACTION_START_LISTENING
                }
                context.startService(serviceIntent)
            }
        }
    }
}
