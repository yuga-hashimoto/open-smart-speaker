package com.opensmarthome.speaker.assistant.provider.embedded

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Covers non-resume branches of [ModelDownloader] that [ModelDownloaderResumeTest]
 * does not exercise: detection helpers ([ModelDownloader.isModelDownloaded],
 * [ModelDownloader.getModelPath]), [ModelDownloader.deleteModel], the HTTP error
 * state, and old-model cleanup on a successful fresh download.
 */
class ModelDownloaderCoverageTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File
    private lateinit var context: Context
    private lateinit var downloader: ModelDownloader

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        tempDir = Files.createTempDirectory("model-dl-coverage-").toFile()
        context = mockk()
        every { context.filesDir } returns tempDir
        downloader = ModelDownloader(context)
    }

    @AfterEach
    fun teardown() {
        runCatching { server.shutdown() }
        tempDir.deleteRecursively()
    }

    private fun availableModel(filename: String = "test.task") = AvailableModel(
        id = "test/$filename",
        displayName = "Test Model",
        url = server.url("/model/$filename").toString(),
        filename = filename,
        sizeMb = 1
    )

    private fun bodyOfSize(bytes: Int): Buffer {
        val buffer = Buffer()
        repeat(bytes) { buffer.writeByte(it and 0xFF) }
        return buffer
    }

    private fun modelsDir(): File = File(tempDir, "models").apply { mkdirs() }

    @Test
    fun `isModelDownloaded returns false when no files present`() {
        assertThat(downloader.isModelDownloaded()).isFalse()
        assertThat(downloader.getModelPath()).isNull()
    }

    @Test
    fun `isModelDownloaded ignores files below the size threshold`() {
        // 1024-byte floor: a 1024-byte (or smaller) file is treated as incomplete.
        File(modelsDir(), "tiny.task").writeBytes(ByteArray(1024))

        assertThat(downloader.isModelDownloaded()).isFalse()
        assertThat(downloader.getModelPath()).isNull()
    }

    @Test
    fun `isModelDownloaded ignores files with unknown extensions`() {
        File(modelsDir(), "readme.txt").writeBytes(ByteArray(4096))

        assertThat(downloader.isModelDownloaded()).isFalse()
        assertThat(downloader.getModelPath()).isNull()
    }

    @Test
    fun `getModelPath returns path for a valid model file`() {
        val dir = modelsDir()
        val valid = File(dir, "model.litertlm").apply { writeBytes(ByteArray(4096)) }

        assertThat(downloader.isModelDownloaded()).isTrue()
        assertThat(downloader.getModelPath()).isEqualTo(valid.absolutePath)
    }

    @Test
    fun `deleteModel wipes all files and resets state to NotStarted`() {
        val dir = modelsDir()
        File(dir, "model.task").writeBytes(ByteArray(4096))
        File(dir, "model.task.downloading").writeBytes(ByteArray(128))
        File(dir, "notes.txt").writeBytes(ByteArray(16))
        assertThat(dir.listFiles()).hasLength(3)

        downloader.deleteModel()

        assertThat(dir.listFiles()).isEmpty()
        assertThat(downloader.state.value).isEqualTo(ModelDownloadState.NotStarted)
    }

    @Test
    fun `HTTP 404 response sets state to Error with status code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        downloader.downloadModel(availableModel("missing.task"))

        val state = downloader.state.value
        assertThat(state).isInstanceOf(ModelDownloadState.Error::class.java)
        assertThat((state as ModelDownloadState.Error).message).isEqualTo("HTTP 404")
    }

    @Test
    fun `successful fresh download deletes pre-existing old model files`() = runTest {
        val dir = modelsDir()
        val oldModel = File(dir, "old-model.task").apply { writeBytes(ByteArray(4096)) }
        val staleNote = File(dir, "stale.txt").apply { writeBytes(ByteArray(32)) }

        val payload = bodyOfSize(2048)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", "2048")
                .setBody(payload)
        )

        downloader.downloadModel(availableModel("new.task"))

        assertThat(oldModel.exists()).isFalse()
        assertThat(staleNote.exists()).isFalse()
        val newModel = File(dir, "new.task")
        assertThat(newModel.exists()).isTrue()
        assertThat(newModel.length()).isEqualTo(2048L)
        assertThat(downloader.state.value).isInstanceOf(ModelDownloadState.Ready::class.java)
    }
}
