package com.opendash.app.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import timber.log.Timber

/**
 * Quick Settings tile that triggers a voice listening session when the user
 * taps it from the system's quick-settings drawer. Useful on tablets where
 * the speaker is wall-mounted and a physical wake-word is too noisy, or
 * during silent hours when hotword is paused.
 *
 * Requires API 24+ (TileService). Manifest declares the service under the
 * `android.service.quicksettings.action.QS_TILE` filter so the user can
 * add it to their tile strip once.
 */
@RequiresApi(Build.VERSION_CODES.N)
class VoiceTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        // The tile is idle (gray-out) by default. Marking it ACTIVE tells the user
        // the app is ready to receive a tap. We don't keep state across sessions.
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = getString(com.opendash.app.R.string.voice_tile_label)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        Timber.d("VoiceTileService click — dispatching to VoiceService")
        // Hide the QS panel before the listening overlay appears so the user
        // isn't left looking at tiles while the assistant wakes up.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // No-op: on API 34+ the system collapses automatically on Intent dispatch.
            } else {
                @Suppress("DEPRECATION")
                unlockAndRun { VoiceService.triggerListening(applicationContext) }
                return
            }
        }.onFailure { Timber.w(it, "Tile unlockAndRun threw") }

        VoiceService.triggerListening(applicationContext)
    }
}
