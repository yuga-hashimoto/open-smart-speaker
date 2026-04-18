package com.opendash.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DocumentChunkDao {

    @Insert
    suspend fun insertAll(chunks: List<DocumentChunkEntity>)

    @Query("SELECT * FROM document_chunk WHERE documentId = :documentId ORDER BY chunkIndex")
    suspend fun getDocument(documentId: String): List<DocumentChunkEntity>

    @Query("SELECT DISTINCT documentId, title FROM document_chunk ORDER BY createdAtMs DESC")
    suspend fun listDocuments(): List<DocumentTitle>

    @Query("SELECT * FROM document_chunk LIMIT :limit")
    suspend fun listAllChunks(limit: Int): List<DocumentChunkEntity>

    @Query("DELETE FROM document_chunk WHERE documentId = :documentId")
    suspend fun deleteDocument(documentId: String): Int

    @Query("DELETE FROM document_chunk")
    suspend fun clear()

    data class DocumentTitle(val documentId: String, val title: String)
}
