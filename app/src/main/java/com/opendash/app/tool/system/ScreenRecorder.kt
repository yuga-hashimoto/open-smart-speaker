package com.opendash.app.tool.system

/**
 * Records the device screen. Requires MediaProjection permission, which must be
 * requested from an Activity via startActivityForResult.
 *
 * Like CameraProvider, implementations are Activity-bound and register themselves
 * through ScreenRecorderHolder while foregrounded.
 */
interface ScreenRecorder {
    suspend fun start(request: RecordRequest): StartResult
    suspend fun stop(): StopResult
    fun isRecording(): Boolean
    fun isReady(): Boolean
}

data class RecordRequest(
    val maxDurationSec: Int = 30,
    val includeAudio: Boolean = false
)

sealed class StartResult {
    data class Started(val outputPath: String) : StartResult()
    data class Failed(val reason: String) : StartResult()
    object NeedsUserConsent : StartResult()
}

sealed class StopResult {
    data class Stopped(val outputPath: String, val durationMs: Long) : StopResult()
    object NotRecording : StopResult()
    data class Failed(val reason: String) : StopResult()
}
