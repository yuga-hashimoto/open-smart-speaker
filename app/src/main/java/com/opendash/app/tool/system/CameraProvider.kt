package com.opendash.app.tool.system

/**
 * Captures a photo via the device camera.
 *
 * Implementation is Activity-bound — a real CameraX/Camera2 or ACTION_IMAGE_CAPTURE
 * integration must be registered from the current Activity because capture requires
 * a LifecycleOwner or for-result launcher.
 *
 * The tool layer holds a CameraProvider reference; Activities register themselves
 * as the provider on onResume and clear on onPause. If no Activity is active the
 * tool returns an error instructing the user to open the app.
 */
interface CameraProvider {
    suspend fun capture(request: CaptureRequest): CaptureResult
    fun isReady(): Boolean
}

data class CaptureRequest(
    val facing: Facing = Facing.BACK,
    val maxWidthPx: Int = 1920
) {
    enum class Facing { FRONT, BACK }
}

sealed class CaptureResult {
    data class Success(val imageBytes: ByteArray, val mimeType: String) : CaptureResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return mimeType == other.mimeType && imageBytes.contentEquals(other.imageBytes)
        }
        override fun hashCode(): Int = 31 * mimeType.hashCode() + imageBytes.contentHashCode()
    }
    data class Failed(val reason: String) : CaptureResult()
    object NotReady : CaptureResult()
}
