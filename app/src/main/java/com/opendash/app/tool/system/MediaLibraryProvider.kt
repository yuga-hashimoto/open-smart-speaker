package com.opendash.app.tool.system

interface MediaLibraryProvider {
    fun hasAudioPermission(): Boolean
    fun hasVideoPermission(): Boolean
    suspend fun recentAudio(limit: Int = 20): List<AudioTrackInfo>
    suspend fun recentVideos(limit: Int = 20): List<VideoInfo>
}

data class AudioTrackInfo(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sizeBytes: Long
)

data class VideoInfo(
    val uri: String,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val takenMs: Long
)
