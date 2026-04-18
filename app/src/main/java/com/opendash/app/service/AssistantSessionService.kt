package com.opendash.app.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import timber.log.Timber

/**
 * Creates VoiceInteractionSessions when the user activates the assistant
 * via home button long-press.
 */
class AssistantSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        Timber.d("Creating new assistant session from home button")
        return AssistantSession(this)
    }
}

class AssistantSession(context: android.content.Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Timber.d("Assistant session shown via home button long-press")
        // Launch main activity with voice trigger
        val intent = android.content.Intent(context, com.opendash.app.MainActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("trigger_voice", true)
        }
        context.startActivity(intent)
        finish()
    }
}
