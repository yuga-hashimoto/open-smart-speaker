package com.opendash.app.voice.wakeword

import kotlinx.coroutines.flow.StateFlow

interface WakeWordDetector {
    fun start(onDetected: () -> Unit)
    fun stop()
    val isListening: StateFlow<Boolean>
}
