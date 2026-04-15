package com.opensmarthome.speaker.voice.wakeword

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    data object Extracting : DownloadState()
    data object Complete : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class VoskModelDownloader(private val context: Context) {

    companion object {
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        private const val MODEL_DIR_NAME = "vosk-model"
    }

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    fun getModelDir(): File = File(context.filesDir, MODEL_DIR_NAME)

    fun isModelDownloaded(): Boolean {
        val dir = getModelDir()
        return dir.exists() && dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
    }

    suspend fun downloadModel() = withContext(Dispatchers.IO) {
        if (isModelDownloaded()) {
            _state.value = DownloadState.Complete
            return@withContext
        }

        try {
            _state.value = DownloadState.Downloading(0f)

            val zipFile = File(context.cacheDir, "vosk-model.zip")
            val url = URL(MODEL_URL)
            val connection = url.openConnection()
            val totalSize = connection.contentLengthLong
            var downloaded = 0L

            connection.getInputStream().use { input ->
                FileOutputStream(zipFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        if (totalSize > 0) {
                            _state.value = DownloadState.Downloading(downloaded.toFloat() / totalSize)
                        }
                    }
                }
            }

            _state.value = DownloadState.Extracting
            extractZip(zipFile, getModelDir())
            zipFile.delete()

            _state.value = DownloadState.Complete
            Timber.d("Vosk model downloaded and extracted to ${getModelDir()}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to download Vosk model")
            _state.value = DownloadState.Error(e.message ?: "Download failed")
        }
    }

    private fun extractZip(zipFile: File, targetDir: File) {
        if (!targetDir.exists()) targetDir.mkdirs()

        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name.substringAfter("/"))
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { output ->
                        zis.copyTo(output)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
