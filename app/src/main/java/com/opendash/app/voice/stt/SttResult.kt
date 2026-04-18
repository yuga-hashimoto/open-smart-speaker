package com.opendash.app.voice.stt

sealed class SttResult {
    data class Partial(val text: String) : SttResult()
    data class Final(val text: String, val confidence: Float = 1.0f) : SttResult()
    data class Error(val message: String) : SttResult()
}
