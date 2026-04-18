package com.opendash.app.tool.system

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Real ScreenRecorder backed by MediaProjection + MediaRecorder. Records the
 * device display to an MP4 in the app's cache dir.
 *
 * Consent is requested via ActivityResultContracts.StartActivityForResult. The
 * launcher must be registered before onStart(), so MainActivity constructs this
 * in onCreate() and installs it in ScreenRecorderHolder.
 */
class MediaProjectionScreenRecorder(
    private val activity: ComponentActivity
) : ScreenRecorder {

    private val projectionManager =
        activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    @Volatile private var mediaProjection: MediaProjection? = null
    @Volatile private var virtualDisplay: VirtualDisplay? = null
    @Volatile private var mediaRecorder: MediaRecorder? = null
    @Volatile private var currentOutputPath: String? = null
    @Volatile private var startTimeMs: Long = 0L

    private val pendingStart = AtomicReference<((StartResult) -> Unit)?>(null)
    private val pendingRequest = AtomicReference<RecordRequest?>(null)

    private val consentLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = pendingStart.getAndSet(null) ?: return@registerForActivityResult
            val request = pendingRequest.getAndSet(null)
            if (result.resultCode != android.app.Activity.RESULT_OK || result.data == null || request == null) {
                callback(StartResult.Failed("consent denied"))
                return@registerForActivityResult
            }
            callback(doStart(result.resultCode, result.data!!, request))
        }

    override suspend fun start(request: RecordRequest): StartResult =
        suspendCancellableCoroutine { cont ->
            if (mediaProjection != null) {
                cont.resume(StartResult.Failed("already recording"))
                return@suspendCancellableCoroutine
            }
            pendingStart.set { r -> cont.resume(r) }
            pendingRequest.set(request)
            try {
                consentLauncher.launch(projectionManager.createScreenCaptureIntent())
            } catch (e: Exception) {
                pendingStart.set(null)
                pendingRequest.set(null)
                cont.resume(StartResult.Failed(e.message ?: "failed to request consent"))
            }
            cont.invokeOnCancellation {
                pendingStart.set(null)
                pendingRequest.set(null)
            }
        }

    override suspend fun stop(): StopResult {
        val out = currentOutputPath ?: return StopResult.NotRecording
        val elapsed = System.currentTimeMillis() - startTimeMs
        return try {
            mediaRecorder?.let {
                try { it.stop() } catch (_: Exception) {}
                it.release()
            }
            mediaRecorder = null
            virtualDisplay?.release(); virtualDisplay = null
            mediaProjection?.stop(); mediaProjection = null
            currentOutputPath = null
            StopResult.Stopped(outputPath = out, durationMs = elapsed)
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop recording")
            StopResult.Failed(e.message ?: "stop error")
        }
    }

    override fun isRecording(): Boolean = mediaProjection != null

    override fun isReady(): Boolean = true

    private fun doStart(code: Int, data: Intent, request: RecordRequest): StartResult {
        val projection = projectionManager.getMediaProjection(code, data)
            ?: return StartResult.Failed("MediaProjection unavailable")

        val outFile = File(activity.cacheDir, "screen").apply { mkdirs() }
            .let { File(it, "rec_${UUID.randomUUID()}.mp4") }
        val metrics = DisplayMetrics().also {
            (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.getMetrics(it)
        }

        val recorder = MediaRecorder().apply {
            if (request.includeAudio) setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(outFile.absolutePath)
            setVideoSize(metrics.widthPixels, metrics.heightPixels)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(5_000_000)
            setMaxDuration(request.maxDurationSec.coerceAtLeast(1) * 1000)
            if (request.includeAudio) setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
        }

        return try {
            val display = projection.createVirtualDisplay(
                "OpenDashRecording",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            ) ?: run {
                recorder.release()
                projection.stop()
                return StartResult.Failed("virtual display creation failed")
            }
            recorder.start()
            this.mediaProjection = projection
            this.virtualDisplay = display
            this.mediaRecorder = recorder
            this.currentOutputPath = outFile.absolutePath
            this.startTimeMs = System.currentTimeMillis()
            StartResult.Started(outputPath = outFile.absolutePath)
        } catch (e: Exception) {
            Timber.e(e, "Failed to begin screen recording")
            try { recorder.release() } catch (_: Exception) {}
            try { projection.stop() } catch (_: Exception) {}
            StartResult.Failed(e.message ?: "recorder start failed")
        }
    }
}
