package com.opensmarthome.speaker.assistant.provider.embedded

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Hardware profile for selecting inference settings.
 *
 * Stolen from off-grid-mobile-ai/src/services/hardware.ts
 * Memory tier → model size cap + GPU layer cap.
 * Over-threading onto E-cores (Cortex-A55) hurts performance,
 * so we target P-cores only (conservative 4-8 threads).
 */
data class HardwareProfile(
    val tier: MemoryTier,
    val totalRamMb: Long,
    val recommendedParamsB: Int,
    val suggestedGpuLayers: Int,
    val recommendedThreads: Int
) {

    enum class MemoryTier {
        LOW_3_4,        // 3-4 GB  → 1.5B params Q4_K_M
        MID_4_6,        // 4-6 GB  → 3B params
        HIGH_6_8,       // 6-8 GB  → 4B params
        FLAGSHIP_8_12,  // 8-12 GB → 8B params
        TOP_12_PLUS     // 12+ GB  → 13B+ params
    }

    companion object {
        fun fromRamMb(ramMb: Long): HardwareProfile {
            val tier = when {
                ramMb < 4_000 -> MemoryTier.LOW_3_4
                ramMb < 6_000 -> MemoryTier.MID_4_6
                ramMb < 8_000 -> MemoryTier.HIGH_6_8
                ramMb < 12_000 -> MemoryTier.FLAGSHIP_8_12
                else -> MemoryTier.TOP_12_PLUS
            }

            val recommendedParamsB = when (tier) {
                MemoryTier.LOW_3_4 -> 2
                MemoryTier.MID_4_6 -> 3
                MemoryTier.HIGH_6_8 -> 4
                MemoryTier.FLAGSHIP_8_12 -> 8
                MemoryTier.TOP_12_PLUS -> 13
            }

            val gpuLayers = when (tier) {
                MemoryTier.LOW_3_4 -> 16
                MemoryTier.MID_4_6 -> 24
                MemoryTier.HIGH_6_8 -> 32
                MemoryTier.FLAGSHIP_8_12 -> 48
                MemoryTier.TOP_12_PLUS -> 99
            }

            // Conservative: 4 threads by default (targeting P-cores)
            // Scale up slightly for high-end devices
            val threads = when (tier) {
                MemoryTier.LOW_3_4 -> 2
                MemoryTier.MID_4_6 -> 4
                MemoryTier.HIGH_6_8 -> 4
                MemoryTier.FLAGSHIP_8_12 -> 6
                MemoryTier.TOP_12_PLUS -> 8
            }

            return HardwareProfile(
                tier = tier,
                totalRamMb = ramMb,
                recommendedParamsB = recommendedParamsB,
                suggestedGpuLayers = gpuLayers,
                recommendedThreads = threads
            )
        }

        fun fromContext(context: Context): HardwareProfile {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val totalMb = memInfo.totalMem / (1024 * 1024)
            return fromRamMb(totalMb)
        }

        /**
         * Build info for logging and diagnostics.
         */
        fun deviceDescription(): String =
            "${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT}, ${Build.SUPPORTED_ABIS.firstOrNull()})"
    }
}
