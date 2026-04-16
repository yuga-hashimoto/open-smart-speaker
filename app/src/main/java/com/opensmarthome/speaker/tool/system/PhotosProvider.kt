package com.opensmarthome.speaker.tool.system

/**
 * Lists recent photos from the device gallery.
 * Returns URIs only — not bytes — so the tool is cheap to call.
 * OpenClaw photos.latest equivalent.
 */
interface PhotosProvider {
    suspend fun getLatest(limit: Int = 10): List<PhotoInfo>
    fun hasPermission(): Boolean
}

data class PhotoInfo(
    val uri: String,
    val displayName: String,
    val takenAtMs: Long,
    val widthPx: Int,
    val heightPx: Int,
    val sizeBytes: Long
)
