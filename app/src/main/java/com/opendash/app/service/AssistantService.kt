package com.opendash.app.service

import android.service.voice.VoiceInteractionService
import timber.log.Timber

/**
 * VoiceInteractionService that allows this app to be set as the
 * device's default digital assistant (Settings > Apps > Default Apps > Digital Assistant).
 *
 * When the user long-presses the Home button, this service is activated.
 */
class AssistantService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        Timber.d("AssistantService ready (home button long-press handler)")
    }
}
