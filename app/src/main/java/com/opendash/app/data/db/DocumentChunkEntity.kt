package com.opendash.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A chunk of an ingested user document for RAG.
 * Multiple chunks share the same documentId.
 */
@Entity(tableName = "document_chunk")
data class DocumentChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: String,
    val title: String,
    val chunkIndex: Int,
    val content: String,
    val createdAtMs: Long
)
