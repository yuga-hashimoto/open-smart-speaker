package com.opendash.app.tool.system

/**
 * Controls device volume.
 * Implementation uses Android AudioManager.
 */
interface VolumeManager {
    suspend fun setVolume(level: Int): Boolean

    /**
     * Nudge the current volume by [steps] hardware-style steps (positive to
     * raise, negative to lower). One step is the same delta the volume keys
     * apply — for STREAM_MUSIC that's typically 1/15 of max.
     *
     * Returns the new volume level as a percentage (0–100) on success, or
     * `null` if the adjustment failed. Does not show the system volume UI —
     * a voice-driven smart speaker confirms the change via TTS instead.
     */
    suspend fun adjustVolume(steps: Int): Int?

    suspend fun getVolume(): Int
    suspend fun mute(): Boolean
    suspend fun unmute(): Boolean
}
