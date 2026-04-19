package com.opendash.app.voice.wakeword

data class WakeWordConfig(
    val keyword: String = "dash",
    val modelPath: String = "",
    val sensitivity: Float = 0.6f
)
