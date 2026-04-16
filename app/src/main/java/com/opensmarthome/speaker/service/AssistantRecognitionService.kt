package com.opensmarthome.speaker.service

import android.content.Intent
import android.speech.RecognitionService
import timber.log.Timber

/**
 * Minimal RecognitionService required for VoiceInteractionService to appear
 * in the system's "Default digital assistant app" list.
 *
 * Reference: OpenClaw Assistant OpenClawAssistantRecognitionService.kt
 * Actual recognition is handled by AndroidSttProvider in VoicePipeline.
 */
class AssistantRecognitionService : RecognitionService() {

    override fun onStartListening(intent: Intent?, listener: Callback?) {
        Timber.d("AssistantRecognitionService: onStartListening (no-op)")
    }

    override fun onCancel(listener: Callback?) {
        Timber.d("AssistantRecognitionService: onCancel")
    }

    override fun onStopListening(listener: Callback?) {
        Timber.d("AssistantRecognitionService: onStopListening")
    }
}
