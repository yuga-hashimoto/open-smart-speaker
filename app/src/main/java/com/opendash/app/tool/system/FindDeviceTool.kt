package com.opendash.app.tool.system

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * "Find my device" — plays the system alarm ringtone on full volume and
 * vibrates for [DEFAULT_DURATION_MS]. Useful when the tablet is buried
 * under cushions and the user needs to find it from across the room.
 */
class FindDeviceTool(
    private val context: Context
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "find_device",
            description = "Make this device ring loudly and vibrate for a few seconds so the user can locate it.",
            parameters = mapOf(
                "duration_seconds" to ToolParameter(
                    "number",
                    "How long to ring (1-30, default 10)",
                    required = false
                )
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        if (call.name != "find_device") {
            return ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
        }
        val seconds = (call.arguments["duration_seconds"] as? Number)?.toInt()?.coerceIn(1, 30) ?: 10
        return try {
            playRingAndVibrate(seconds)
            ToolResult(call.id, true, """{"rang_for_seconds":$seconds}""")
        } catch (e: Exception) {
            Timber.e(e, "find_device failed")
            ToolResult(call.id, false, "", e.message ?: "find_device error")
        }
    }

    private suspend fun playRingAndVibrate(seconds: Int) = withContext(Dispatchers.IO) {
        val vibrator = vibrator()
        val player = MediaPlayer().apply {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            setDataSource(context, uri)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            prepare()
            start()
        }

        // Vibration pattern: short pulses
        val pattern = longArrayOf(0, 600, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }

        try {
            delay(seconds * 1000L)
        } finally {
            try { player.stop() } catch (_: Exception) {}
            try { player.release() } catch (_: Exception) {}
            vibrator?.cancel()
        }
    }

    private fun vibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    companion object {
        const val DEFAULT_DURATION_MS = 10_000L
    }
}
