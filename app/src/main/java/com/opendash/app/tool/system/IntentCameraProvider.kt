package com.opendash.app.tool.system

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Minimal non-CameraX camera provider using ACTION_IMAGE_CAPTURE. Works without
 * extra dependencies and gives users the stock camera UI.
 *
 * Activities register themselves before onStart() by calling [attach] with their
 * ComponentActivity reference; [detach] tears down the launcher on onDestroy.
 * The tool layer never touches an Activity directly — it just calls capture()
 * and the registered launcher dispatches the Intent.
 */
class IntentCameraProvider(
    private val activity: ComponentActivity,
    private val authority: String = "${activity.packageName}.fileprovider"
) : CameraProvider {

    private val pending = AtomicReference<((CaptureResult) -> Unit)?>(null)
    private val pendingUri = AtomicReference<Uri?>(null)
    private val pendingFile = AtomicReference<File?>(null)

    private val launcher: ActivityResultLauncher<Uri> =
        activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val callback = pending.getAndSet(null) ?: return@registerForActivityResult
            val uri = pendingUri.getAndSet(null)
            val file = pendingFile.getAndSet(null)
            val result = if (success && uri != null && file != null) {
                readImageBytes(file)
            } else {
                CaptureResult.Failed("capture cancelled or failed")
            }
            callback(result)
        }

    override suspend fun capture(request: CaptureRequest): CaptureResult =
        suspendCancellableCoroutine { cont ->
            val file = createImageFile(activity)
            val uri = FileProvider.getUriForFile(activity, authority, file)
            pendingFile.set(file)
            pendingUri.set(uri)
            pending.set { r -> cont.resume(r) }
            try {
                launcher.launch(uri)
            } catch (e: Exception) {
                pending.set(null)
                pendingFile.set(null)
                pendingUri.set(null)
                cont.resume(CaptureResult.Failed(e.message ?: "failed to launch camera"))
            }
            cont.invokeOnCancellation {
                pending.set(null)
                pendingFile.set(null)
                pendingUri.set(null)
            }
        }

    override fun isReady(): Boolean = true

    private fun createImageFile(context: Context): File {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        return File(dir, "capture_${UUID.randomUUID()}.jpg")
    }

    private fun readImageBytes(file: File): CaptureResult {
        return try {
            if (!file.exists() || file.length() == 0L) {
                CaptureResult.Failed("image file missing or empty")
            } else {
                val bytes = FileInputStream(file).use { stream ->
                    val out = ByteArrayOutputStream()
                    stream.copyTo(out)
                    out.toByteArray()
                }
                CaptureResult.Success(imageBytes = bytes, mimeType = "image/jpeg")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to read captured image")
            CaptureResult.Failed(e.message ?: "image read error")
        } finally {
            runCatching { file.delete() }
        }
    }

    /** Exposed for tests that want to skip Bitmap/FileProvider wiring. */
    internal fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int = 85): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
