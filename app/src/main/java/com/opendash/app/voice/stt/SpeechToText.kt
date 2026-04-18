package com.opendash.app.voice.stt

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SpeechToText {
    fun startListening(): Flow<SttResult>
    fun stopListening()
    val isListening: StateFlow<Boolean>
}
