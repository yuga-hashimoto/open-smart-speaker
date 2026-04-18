package com.opendash.app.voice

import android.content.Context
import android.hardware.SensorPrivacyManager
import android.media.AudioManager
import android.os.Build
import timber.log.Timber

object MicrophoneChecker {

    fun isMicrophoneAvailable(context: Context): Boolean {
        // Check Android 12+ sensor privacy (hardware mic toggle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val spm = context.getSystemService(SensorPrivacyManager::class.java)
                if (spm?.supportsSensorToggle(SensorPrivacyManager.Sensors.MICROPHONE) == true) {
                    // Use AudioManager as proxy since isSensorPrivacyEnabled requires higher API
                    val audioManager = context.getSystemService(AudioManager::class.java)
                    if (audioManager?.isMicrophoneMute == true) {
                        Timber.w("Microphone blocked by sensor privacy or mute")
                        return false
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to check sensor privacy")
            }
        }

        // Check if mic is muted via AudioManager
        try {
            val audioManager = context.getSystemService(AudioManager::class.java)
            if (audioManager?.isMicrophoneMute == true) {
                Timber.w("Microphone is muted")
                return false
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to check mic mute state")
        }

        return true
    }
}
