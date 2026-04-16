package com.opensmarthome.speaker.assistant.provider.embedded

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class ModelDownloadState {
    data object NotStarted : ModelDownloadState()
    data object Checking : ModelDownloadState()
    data class Downloading(val progress: Float, val downloadedMb: Int, val totalMb: Int) : ModelDownloadState()
    data object Ready : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}

data class AvailableModel(
    val id: String,
    val displayName: String,
    val url: String,
    val filename: String,
    val sizeMb: Int,
    val downloads: Int = 0
)

class ModelDownloader(private val context: Context) {

    companion object {
        private const val MIN_STORAGE_MB = 400L
        private const val HF_API = "https://huggingface.co/api/models"
        private const val HF_AUTHOR = "litert-community"
        // Model repos known to have .task files for LLM inference
        // Repos with .task (MediaPipe) or .litertlm (LiteRT-LM) files
        private val LLM_REPOS = listOf(
            "gemma-4-E2B-it-litert-lm",
            "gemma-4-E4B-it-litert-lm",
            "Gemma3-1B-IT",
            "DeepSeek-R1-Distill-Qwen-1.5B",
            "Qwen2.5-1.5B-Instruct",
            "Phi-4-mini-instruct"
        )
    }

    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotStarted)
    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    private val _availableModels = MutableStateFlow<List<AvailableModel>>(emptyList())
    val availableModels: StateFlow<List<AvailableModel>> = _availableModels.asStateFlow()

    private val _selectedModel = MutableStateFlow<AvailableModel?>(null)
    val selectedModel: StateFlow<AvailableModel?> = _selectedModel.asStateFlow()

    private val modelsDir: File
        get() {
            val dir = File(context.filesDir, "models")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun selectModel(model: AvailableModel) {
        _selectedModel.value = model
    }

    fun getModelPath(): String? {
        return modelsDir.listFiles()?.firstOrNull {
            it.extension in listOf("task", "tflite", "bin", "litertlm") && it.length() > 1024
        }?.absolutePath
    }

    fun isModelDownloaded(): Boolean = getModelPath() != null

    suspend fun fetchAvailableModels() = withContext(Dispatchers.IO) {
        try {
            val models = mutableListOf<AvailableModel>()

            for (repoName in LLM_REPOS) {
                try {
                    val repoId = "$HF_AUTHOR/$repoName"
                    val treeUrl = URL("https://huggingface.co/api/models/$repoId/tree/main")
                    val conn = treeUrl.openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000

                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        val json = conn.inputStream.bufferedReader().readText()
                        val files = JSONArray(json)

                        for (i in 0 until files.length()) {
                            val file = files.getJSONObject(i)
                            val path = file.getString("path")
                            val size = file.optLong("size", 0)

                            // .task (MediaPipe) and .litertlm (LiteRT-LM) files
                            val isWebOnly = path.contains("-web.") || path.contains("_web.")
                            val isModelFile = path.endsWith(".task") || path.endsWith(".litertlm")
                            val isQualcomm = path.contains("qualcomm") // skip device-specific builds
                            if (isModelFile && !isWebOnly && !isQualcomm && size > 1_000_000 && size < 4_000_000_000L) {
                                val sizeMb = (size / 1_048_576).toInt()
                                val displayName = "$repoName / ${path.removeSuffix(".task")}"
                                models.add(
                                    AvailableModel(
                                        id = "$repoId/$path",
                                        displayName = displayName,
                                        url = "https://huggingface.co/$repoId/resolve/main/$path",
                                        filename = path.replace("/", "_"),
                                        sizeMb = sizeMb
                                    )
                                )
                            }
                        }
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Timber.w("Failed to fetch models from $repoName: ${e.message}")
                }
            }

            // Sort by size (smallest first)
            _availableModels.value = models.sortedBy { it.sizeMb }

            if (models.isNotEmpty() && _selectedModel.value == null) {
                // Auto-select first Gemma 4 model, or first available
                val default = models.firstOrNull { it.id.contains("gemma-4") } ?: models.first()
                _selectedModel.value = default
            }

            Timber.d("Fetched ${models.size} available models")
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch model list")
        }
    }

    suspend fun ensureModelAvailable() {
        if (isModelDownloaded()) {
            _state.value = ModelDownloadState.Ready
            return
        }
        // Don't auto-download, wait for user selection
    }

    suspend fun downloadSelectedModel() {
        val model = _selectedModel.value ?: return
        downloadModel(model)
    }

    suspend fun downloadModel(model: AvailableModel) = withContext(Dispatchers.IO) {
        val targetFile = File(modelsDir, model.filename)
        val tempFile = File(modelsDir, "${model.filename}.downloading")

        try {
            _state.value = ModelDownloadState.Downloading(0f, 0, model.sizeMb)
            Timber.d("Downloading: ${model.displayName} from ${model.url}")

            val url = URL(model.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "open-smart-speaker/1.0")
            connection.instanceFollowRedirects = true

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                _state.value = ModelDownloadState.Error("HTTP $responseCode")
                return@withContext
            }

            val totalBytes = connection.contentLengthLong
            val totalMb = if (totalBytes > 0) (totalBytes / 1_048_576).toInt() else model.sizeMb
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                        _state.value = ModelDownloadState.Downloading(
                            progress, (downloadedBytes / 1_048_576).toInt(), totalMb
                        )
                    }
                }
            }

            // Delete old models
            modelsDir.listFiles()?.filter { it != tempFile }?.forEach { it.delete() }

            if (tempFile.renameTo(targetFile)) {
                Timber.d("Downloaded: ${targetFile.name} (${targetFile.length() / 1_048_576}MB)")
                _state.value = ModelDownloadState.Ready
            } else {
                _state.value = ModelDownloadState.Error("Failed to save file")
            }
        } catch (e: Exception) {
            Timber.e(e, "Download failed")
            tempFile.delete()
            _state.value = ModelDownloadState.Error("${e.message}")
        }
    }

    fun deleteModel() {
        modelsDir.listFiles()?.forEach { it.delete() }
        _state.value = ModelDownloadState.NotStarted
    }
}
