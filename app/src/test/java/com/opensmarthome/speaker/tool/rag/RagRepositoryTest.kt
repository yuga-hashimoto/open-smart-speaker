package com.opensmarthome.speaker.tool.rag

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.data.db.DocumentChunkDao
import com.opensmarthome.speaker.data.db.DocumentChunkEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RagRepositoryTest {

    private lateinit var dao: FakeDocumentChunkDao
    private lateinit var service: RagService
    private lateinit var repo: RagRepository

    @BeforeEach
    fun setup() {
        dao = FakeDocumentChunkDao()
        service = RagService(dao)
        repo = RagRepository(service, dao)
    }

    @Test
    fun `ingest rejects blank title`() = runTest {
        assertThrows<IllegalArgumentException> { repo.ingest("  ", "body") }
    }

    @Test
    fun `ingest rejects blank content`() = runTest {
        assertThrows<IllegalArgumentException> { repo.ingest("title", "  ") }
    }

    @Test
    fun `ingest and listDocuments reports the new document`() = runTest {
        repo.ingest(
            "Android Tips",
            "Battery tips: dim the screen, disable Bluetooth when idle. " +
                "Storage tips: clean downloads folder weekly."
        )
        val docs = repo.listDocuments()
        assertThat(docs).hasSize(1)
        assertThat(docs[0].title).isEqualTo("Android Tips")
        assertThat(docs[0].chunkCount).isGreaterThan(0)
    }

    @Test
    fun `listDocuments aggregates chunk counts per document`() = runTest {
        repo.ingest("A", "Alpha content that's reasonably long to trigger chunking.")
        repo.ingest("B", "Beta content of similar length for the second document.")
        val docs = repo.listDocuments().associateBy { it.title }
        assertThat(docs).containsKey("A")
        assertThat(docs).containsKey("B")
        assertThat(docs["A"]!!.chunkCount).isAtLeast(1)
        assertThat(docs["B"]!!.chunkCount).isAtLeast(1)
    }

    @Test
    fun `delete removes the document and its chunks`() = runTest {
        val id = repo.ingest("Gone", "body-of-doomed-doc")
        assertThat(repo.listDocuments().any { it.id == id }).isTrue()

        val removed = repo.delete(id)

        assertThat(removed).isTrue()
        assertThat(repo.listDocuments().any { it.id == id }).isFalse()
    }

    @Test
    fun `delete unknown id returns false`() = runTest {
        assertThat(repo.delete("no-such-id")).isFalse()
    }

    @Test
    fun `retrieve finds chunks by query`() = runTest {
        repo.ingest("Kitchen", "Bake cookies at 180 degrees celsius for 12 minutes.")
        repo.ingest("Garden", "Water tomatoes every other morning before the sun.")
        val hits = repo.retrieve("cookies")
        assertThat(hits).isNotEmpty()
        assertThat(hits.first().documentTitle).isEqualTo("Kitchen")
    }
}

private class FakeDocumentChunkDao : DocumentChunkDao {
    private val rows = mutableListOf<DocumentChunkEntity>()
    private var nextId = 1L

    override suspend fun insertAll(chunks: List<DocumentChunkEntity>) {
        chunks.forEach { rows.add(it.copy(id = nextId++)) }
    }

    override suspend fun getDocument(documentId: String): List<DocumentChunkEntity> =
        rows.filter { it.documentId == documentId }

    override suspend fun listDocuments(): List<DocumentChunkDao.DocumentTitle> =
        rows.groupBy { it.documentId }
            .map { (id, chunks) -> DocumentChunkDao.DocumentTitle(id, chunks.first().title) }

    override suspend fun listAllChunks(limit: Int): List<DocumentChunkEntity> =
        rows.take(limit)

    override suspend fun deleteDocument(documentId: String): Int {
        val before = rows.size
        rows.removeAll { it.documentId == documentId }
        return before - rows.size
    }

    override suspend fun clear() {
        rows.clear()
    }
}
