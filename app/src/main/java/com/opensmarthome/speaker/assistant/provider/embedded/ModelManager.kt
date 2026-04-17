package com.opensmarthome.speaker.assistant.provider.embedded

import android.content.Context
import android.net.Uri
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

data class ModelInfo(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val sizeMb: String = "%.1f MB".format(sizeBytes / 1_048_576.0)
)

class ModelManager(private val context: Context) {

    fun getModelsDirectory(): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listAvailableModels(): List<ModelInfo> {
        return getModelsDirectory().listFiles()
            ?.filter { it.extension in listOf("bin", "task", "tflite", "gguf", "litertlm") }
            ?.map { ModelInfo(path = it.absolutePath, name = it.nameWithoutExtension, sizeBytes = it.length()) }
            ?: emptyList()
    }

    fun validateModel(path: String): Boolean {
        val file = File(path)
        if (!file.exists() || file.length() < 1024) return false
        return try {
            when (file.extension) {
                "gguf" -> {
                    file.inputStream().use { stream ->
                        val magic = ByteArray(4)
                        stream.read(magic)
                        String(magic) == "GGUF"
                    }
                }
                "bin", "task", "tflite" -> true // MediaPipe / TFLite formats
                else -> false
            }
        } catch (e: Exception) {
            Timber.w(e, "Model validation failed: $path")
            false
        }
    }

    fun importModel(uri: Uri): ModelInfo? {
        return try {
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "model.gguf"
            val targetFile = File(getModelsDirectory(), fileName)

            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }

            if (!validateModel(targetFile.absolutePath)) {
                targetFile.delete()
                Timber.e("Imported file is not a valid GGUF model")
                return null
            }

            Timber.d("Model imported: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            ModelInfo(
                path = targetFile.absolutePath,
                name = targetFile.nameWithoutExtension,
                sizeBytes = targetFile.length()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to import model")
            null
        }
    }

    fun deleteModel(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete model: $path")
            false
        }
    }

    fun hasAvailableStorage(requiredBytes: Long): Boolean {
        val dir = getModelsDirectory()
        return dir.usableSpace > requiredBytes
    }
}
