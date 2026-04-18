package com.opendash.app.tool.rag

import com.google.common.truth.Truth.assertThat
import com.opendash.app.data.db.DocumentChunkDao
import com.opendash.app.tool.ToolCall
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RagToolExecutorTest {

    private lateinit var dao: DocumentChunkDao
    private lateinit var service: RagService
    private lateinit var executor: RagToolExecutor

    @BeforeEach
    fun setup() {
        dao = mockk(relaxed = true)
        service = RagService(dao)
        executor = RagToolExecutor(service)
    }

    @Test
    fun `availableTools has four RAG tools`() = runTest {
        val names = executor.availableTools().map { it.name }
        assertThat(names).containsExactly(
            "ingest_document", "retrieve_document", "list_documents", "delete_document"
        )
    }

    @Test
    fun `ingest_document stores chunks`() = runTest {
        val result = executor.execute(
            ToolCall("1", "ingest_document", mapOf(
                "title" to "My Notes",
                "content" to "Some content to ingest."
            ))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("document_id")
        coVerify { dao.insertAll(match { it.isNotEmpty() }) }
    }

    @Test
    fun `ingest empty content returns error`() = runTest {
        val result = executor.execute(
            ToolCall("2", "ingest_document", mapOf("title" to "t", "content" to "  "))
        )

        assertThat(result.success).isFalse()
    }

    @Test
    fun `retrieve returns matching chunks with score`() = runTest {
        coEvery { dao.listAllChunks(any()) } returns listOf(
            com.opendash.app.data.db.DocumentChunkEntity(
                id = 1, documentId = "d1", title = "Cats", chunkIndex = 0,
                content = "cats are wonderful small furry creatures", createdAtMs = 0L
            ),
            com.opendash.app.data.db.DocumentChunkEntity(
                id = 2, documentId = "d2", title = "Dogs", chunkIndex = 0,
                content = "dogs bark loudly at strangers", createdAtMs = 0L
            )
        )

        val result = executor.execute(
            ToolCall("3", "retrieve_document", mapOf("query" to "furry cats"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("Cats")
        assertThat(result.data).contains("score")
    }

    @Test
    fun `list_documents returns titles`() = runTest {
        coEvery { dao.listDocuments() } returns listOf(
            DocumentChunkDao.DocumentTitle("d1", "First Doc"),
            DocumentChunkDao.DocumentTitle("d2", "Second Doc")
        )

        val result = executor.execute(
            ToolCall("4", "list_documents", emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("First Doc")
        assertThat(result.data).contains("Second Doc")
    }

    @Test
    fun `delete_document not found returns error`() = runTest {
        coEvery { dao.deleteDocument("missing") } returns 0

        val result = executor.execute(
            ToolCall("5", "delete_document", mapOf("id" to "missing"))
        )

        assertThat(result.success).isFalse()
    }
}
