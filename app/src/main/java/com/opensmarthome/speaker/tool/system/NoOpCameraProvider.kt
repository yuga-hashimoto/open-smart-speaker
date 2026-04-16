package com.opensmarthome.speaker.tool.system

/**
 * Default CameraProvider used when no Activity-bound provider is active.
 * Always returns NotReady so the tool can emit a clear user-facing hint.
 */
class NoOpCameraProvider : CameraProvider {
    override suspend fun capture(request: CaptureRequest): CaptureResult = CaptureResult.NotReady
    override fun isReady(): Boolean = false
}
