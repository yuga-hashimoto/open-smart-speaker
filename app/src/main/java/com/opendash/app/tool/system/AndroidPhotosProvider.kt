package com.opendash.app.tool.system

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import timber.log.Timber

class AndroidPhotosProvider(
    private val context: Context
) : PhotosProvider {

    override suspend fun getLatest(limit: Int): List<PhotoInfo> {
        if (!hasPermission()) return emptyList()

        val photos = mutableListOf<PhotoInfo>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE
        )

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT ${limit.coerceIn(1, 50)}"
            )?.use { cursor ->
                while (cursor.moveToNext() && photos.size < limit) {
                    val id = cursor.getLong(0)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    ).toString()
                    val name = cursor.getString(1).orEmpty()
                    val takenMs = cursor.getLong(2)
                        .takeIf { it > 0 } ?: (cursor.getLong(3) * 1000)
                    val width = cursor.getInt(4)
                    val height = cursor.getInt(5)
                    val size = cursor.getLong(6)

                    photos.add(PhotoInfo(uri, name, takenMs, width, height, size))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to query photos")
        }

        return photos
    }

    override fun hasPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
