package com.opendash.app.voice.diagnostics

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorPrivacyManager
import android.media.AudioManager
import android.os.Build
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Diagnostic checks for voice-related subsystems.
 * Reference: OpenClaw Assistant VoiceDiagnostics.
 *
 * Results are consumed by the Settings UI "Voice Health" section to give users
 * actionable feedback when voice features don't work.
 */
object VoiceDiagnostics {

    enum class Severity { OK, WARNING, ERROR }

    data class DiagnosticItem(
        val id: String,
        val title: String,
        val message: String,
        val severity: Severity,
        val actionLabel: String? = null,
        val actionIntent: Intent? = null
    )

    fun run(context: Context): List<DiagnosticItem> {
        val items = mutableListOf<DiagnosticItem>()

        items += checkRecordAudioPermission(context)
        items += checkMicrophonePrivacy(context)
        items += checkSpeechRecognition(context)
        items += checkTtsEngine(context)

        return items
    }

    private fun checkRecordAudioPermission(context: Context): DiagnosticItem {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) {
            DiagnosticItem(
                id = "record_audio",
                title = "Microphone Permission",
                message = "Granted",
                severity = Severity.OK
            )
        } else {
            DiagnosticItem(
                id = "record_audio",
                title = "Microphone Permission",
                message = "Not granted — voice input won't work",
                severity = Severity.ERROR,
                actionLabel = "Open App Settings",
                actionIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                }
            )
        }
    }

    private fun checkMicrophonePrivacy(context: Context): DiagnosticItem {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return DiagnosticItem(
                id = "mic_privacy",
                title = "Microphone Hardware",
                message = "OK",
                severity = Severity.OK
            )
        }
        val spm = context.getSystemService(SensorPrivacyManager::class.java)
        val supports = spm?.supportsSensorToggle(SensorPrivacyManager.Sensors.MICROPHONE) == true
        val audioManager = context.getSystemService(AudioManager::class.java)
        val muted = audioManager?.isMicrophoneMute == true
        return when {
            supports && muted -> DiagnosticItem(
                id = "mic_privacy",
                title = "Microphone Hardware",
                message = "Microphone is muted by system privacy toggle",
                severity = Severity.ERROR,
                actionLabel = "Open Privacy Settings",
                actionIntent = Intent(android.provider.Settings.ACTION_PRIVACY_SETTINGS)
            )
            else -> DiagnosticItem(
                id = "mic_privacy",
                title = "Microphone Hardware",
                message = "OK",
                severity = Severity.OK
            )
        }
    }

    private fun checkSpeechRecognition(context: Context): DiagnosticItem {
        val available = SpeechRecognizer.isRecognitionAvailable(context)
        return if (available) {
            DiagnosticItem(
                id = "stt_availability",
                title = "Speech Recognition",
                message = "Available on this device",
                severity = Severity.OK
            )
        } else {
            DiagnosticItem(
                id = "stt_availability",
                title = "Speech Recognition",
                message = "No speech recognition service installed. Install Google app or another recognition provider.",
                severity = Severity.ERROR,
                actionLabel = "Find in Play Store",
                actionIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("market://details?id=com.google.android.googlequicksearchbox")
                }
            )
        }
    }

    /**
     * Try to discover whether at least one TTS engine is installed.
     * Deeper voice-data checks would require initializing TextToSpeech which is async;
     * here we use the TTS_SERVICE intent query as a cheap first-line check.
     */
    private fun checkTtsEngine(context: Context): DiagnosticItem {
        val pm = context.packageManager
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val services = pm.queryIntentServices(intent, 0)
        return if (services.isNotEmpty()) {
            DiagnosticItem(
                id = "tts_engine",
                title = "Text-to-Speech Engine",
                message = "${services.size} engine(s) detected",
                severity = Severity.OK
            )
        } else {
            DiagnosticItem(
                id = "tts_engine",
                title = "Text-to-Speech Engine",
                message = "No TTS engine installed. Install Google Text-to-Speech.",
                severity = Severity.ERROR,
                actionLabel = "Install Google TTS",
                actionIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("market://details?id=com.google.android.tts")
                }
            )
        }
    }
}
