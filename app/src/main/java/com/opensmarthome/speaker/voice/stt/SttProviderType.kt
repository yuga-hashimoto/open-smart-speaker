package com.opensmarthome.speaker.voice.stt

/**
 * Which backend handles speech-to-text. The system default uses Android's
 * [android.speech.SpeechRecognizer], which relies on Google Play Services or
 * an OEM STT engine and usually requires network — fine for most devices
 * but not acceptable for fully-offline tablets.
 *
 * Offline backends (Vosk / Whisper) are still being wired up — see
 * [OfflineSttStub] for the intended lifecycle. The stub emits a single
 * error result so the pipeline's ErrorClassifier can tell the user that
 * the offline backend isn't ready yet, instead of hanging silently.
 */
enum class SttProviderType(val prefValue: String) {
    /** android.speech.SpeechRecognizer (default, online / GMS). */
    ANDROID("android"),

    /** Vosk offline STT — full transcription, not just keyword spotting. Placeholder. */
    VOSK_OFFLINE("vosk"),

    /** whisper.cpp offline STT via JNI. Placeholder. */
    WHISPER_OFFLINE("whisper");

    companion object {
        fun fromPref(raw: String?): SttProviderType =
            values().firstOrNull { it.prefValue == raw?.lowercase() } ?: ANDROID
    }
}
