package com.opendash.app.tool.system

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import timber.log.Timber
import kotlin.math.abs

/**
 * Android implementation of [VolumeManager] using [AudioManager].
 *
 * Voice-driven: we never pass `AudioManager.FLAG_SHOW_UI`, so the system
 * volume slider overlay never pops up over chat/ambient UI. TTS confirms
 * the change instead.
 *
 * Step-based nudging uses `adjustStreamVolume` with `ADJUST_RAISE` /
 * `ADJUST_LOWER`, matching the hardware volume keys. Stolen from Ava's
 * `VolumeControlService.adjustVolume(isUp)`.
 *
 * WrongConstant is suppressed because 0 ("no flags") is a documented
 * valid value for the `flags` arg, but Lint's `@IntDef(flag = true)`
 * doesn't recognise a bare 0.
 */
@SuppressLint("WrongConstant")
class AndroidVolumeManager(
    private val context: Context
) : VolumeManager {

    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override suspend fun setVolume(level: Int): Boolean {
        return try {
            val am = audioManager
            val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (level * maxVolume / 100).coerceIn(0, maxVolume)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, NO_FLAGS)
            Timber.d("Volume set to $level% ($targetVolume/$maxVolume)")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to set volume")
            false
        }
    }

    override suspend fun adjustVolume(steps: Int): Int? {
        if (steps == 0) return getVolume()
        return try {
            val am = audioManager
            val direction = if (steps > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            repeat(abs(steps)) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, NO_FLAGS)
            }
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val percent = if (max > 0) (current * 100 / max) else 0
            Timber.d("Volume adjusted by $steps → $percent% ($current/$max)")
            percent
        } catch (e: Exception) {
            Timber.e(e, "Failed to adjust volume")
            null
        }
    }

    override suspend fun getVolume(): Int {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) (current * 100 / max) else 0
    }

    override suspend fun mute(): Boolean {
        return try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_MUTE,
                NO_FLAGS
            )
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to mute")
            false
        }
    }

    override suspend fun unmute(): Boolean {
        return try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_UNMUTE,
                NO_FLAGS
            )
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to unmute")
            false
        }
    }

    private companion object {
        const val NO_FLAGS = 0
    }
}
