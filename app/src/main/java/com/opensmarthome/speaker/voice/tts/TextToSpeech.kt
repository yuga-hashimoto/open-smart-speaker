package com.opensmarthome.speaker.voice.tts

import kotlinx.coroutines.flow.StateFlow

interface TextToSpeech {
    suspend fun speak(text: String)
    fun stop()
    val isSpeaking: StateFlow<Boolean>
}
