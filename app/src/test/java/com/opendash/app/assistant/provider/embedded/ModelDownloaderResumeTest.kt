package com.opendash.app.assistant.provider.embedded

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Exercises the resume path of [ModelDownloader.downloadModel]. The download target
 * is served by [MockWebServer] so we can assert on the `Range` header and choose
 * the response code (200 vs 206).
 */
class ModelDownloaderResumeTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File
    private lateinit var context: Context
    private lateinit var downloader: ModelDownloader

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        tempDir = Files.createTempDirectory("model-dl-").toFile()
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

    @Test
    fun `fresh download sends no Range header`() = runTest {
        val payload = bodyOfSize(2048)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", "2048")
                .setBody(payload)
        )

        downloader.downloadModel(availableModel())

        val recorded: RecordedRequest = server.takeRequest()
        assertThat(recorded.getHeader("Range")).isNull()
        assertThat(downloader.state.value).isInstanceOf(ModelDownloadState.Ready::class.java)
    }

    @Test
    fun `partial file triggers Range request on next call`() = runTest {
        val filename = "resume.task"
        val tempFile = File(File(tempDir, "models"), "$filename.downloading").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(512) { (it and 0xFF).toByte() })
        }

        val remaining = bodyOfSize(1536) // 2048 - 512 already on disk
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Length", "1536")
                .setHeader("Content-Range", "bytes 512-2047/2048")
                .setBody(remaining)
        )

        downloader.downloadModel(availableModel(filename))

        val recorded = server.takeRequest()
        assertThat(recorded.getHeader("Range")).isEqualTo("bytes=512-")
        assertThat(tempFile.exists()).isFalse() // renamed to final path after completion
        val finalFile = File(File(tempDir, "models"), filename)
        assertThat(finalFile.exists()).isTrue()
        assertThat(finalFile.length()).isEqualTo(2048L)
    }

    @Test
    fun `server that ignores Range starts fresh instead of appending`() = runTest {
        val filename = "ignored.task"
        val tempFile = File(File(tempDir, "models"), "$filename.downloading").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(512))
        }

        val fullPayload = bodyOfSize(2048)
        server.enqueue(
            MockResponse()
                .setResponseCode(200) // server ignored Range and returned full body
                .setHeader("Content-Length", "2048")
                .setBody(fullPayload)
        )

        downloader.downloadModel(availableModel(filename))

        val finalFile = File(File(tempDir, "models"), filename)
        assertThat(finalFile.exists()).isTrue()
        // We should end at exactly 2048 bytes, not 2048 + 512 appended.
        assertThat(finalFile.length()).isEqualTo(2048L)
        assertThat(downloader.state.value).isInstanceOf(ModelDownloadState.Ready::class.java)
    }

    @Test
    fun `connection failure keeps partial file for future resume`() = runTest {
        val filename = "fail.task"
        val preexistingTemp = File(File(tempDir, "models"), "$filename.downloading").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(512) { (it and 0xFF).toByte() })
        }
        // Capture the URL before taking the server down so `availableModel(...)` can
        // resolve the port, then force the download to fail by shutting the server.
        val model = availableModel(filename)
        server.shutdown()

        downloader.downloadModel(model)

        // The partial file must survive the failure so the next call can resume
        // via Range. The contract is: do not delete on error.
        assertThat(preexistingTemp.exists()).isTrue()
        assertThat(preexistingTemp.length()).isEqualTo(512L)
        assertThat(downloader.state.value).isInstanceOf(ModelDownloadState.Error::class.java)
    }
}
