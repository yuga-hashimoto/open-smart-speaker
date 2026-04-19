package com.opendash.app.tool.system

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import timber.log.Timber

class AndroidMediaLibraryProvider(
    private val context: Context
) : MediaLibraryProvider {

    override fun hasAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, perm) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun hasVideoPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, perm) ==
            PackageManager.PERMISSION_GRANTED
    }

    override suspend fun recentAudio(limit: Int): List<AudioTrackInfo> {
        if (!hasAudioPermission()) return emptyList()

        val tracks = mutableListOf<AudioTrackInfo>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC LIMIT ${limit.coerceIn(1, 100)}"
            )?.use { cursor ->
                while (cursor.moveToNext() && tracks.size < limit) {
                    val id = cursor.getLong(0)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    ).toString()
                    tracks.add(
                        AudioTrackInfo(
                            uri = uri,
                            title = cursor.getString(1).orEmpty(),
                            artist = cursor.getString(2).orEmpty(),
                            album = cursor.getString(3).orEmpty(),
                            durationMs = cursor.getLong(4),
                            sizeBytes = cursor.getLong(5)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to query audio library")
        }

        return tracks
    }

    override suspend fun recentVideos(limit: Int): List<VideoInfo> {
        if (!hasVideoPermission()) return emptyList()

        val videos = mutableListOf<VideoInfo>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DATE_ADDED
        )

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC LIMIT ${limit.coerceIn(1, 100)}"
            )?.use { cursor ->
                while (cursor.moveToNext() && videos.size < limit) {
                    val id = cursor.getLong(0)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                    ).toString()
                    val takenMs = cursor.getLong(6)
                        .takeIf { it > 0 } ?: (cursor.getLong(7) * 1000)
                    videos.add(
                        VideoInfo(
                            uri = uri,
                            name = cursor.getString(1).orEmpty(),
                            durationMs = cursor.getLong(2),
                            sizeBytes = cursor.getLong(3),
                            width = cursor.getInt(4),
                            height = cursor.getInt(5),
                            takenMs = takenMs
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to query video library")
        }

        return videos
    }
}
