package com.example.data.model

import com.squareup.moshi.JsonClass

enum class DocumentType {
    PDF,
    DOCX,
    TXT,
    MANUAL
}

enum class IndexingStatus {
    INDEXED,
    INDEXING,
    ERROR
}

data class ParsedDocument(
    val title: String,
    val text: String,
    val type: DocumentType,
    val fileSizeBytes: Long
)

data class RetrievedChunk(
    val chunkId: Long,
    val documentId: Long,
    val documentTitle: String,
    val fileType: String,
    val chunkIndex: Int,
    val text: String,
    val similarityScore: Float,
    val wordCount: Int
)

@JsonClass(generateAdapter = true)
data class SourceCitation(
    val chunkId: Long,
    val documentId: Long,
    val documentTitle: String,
    val fileType: String,
    val chunkIndex: Int,
    val similarityScore: Float,
    val previewText: String
)

data class StorageStats(
    val totalDocuments: Int,
    val totalChunks: Int,
    val totalWords: Int,
    val totalSizeBytes: Long,
    val vectorIndexSizeBytes: Long
)

data class RagSettings(
    val customApiKey: String = "",
    val offlineModeOnly: Boolean = false,
    val autoIndexOnUpload: Boolean = true,
    val chunkSizeWords: Int = 250,
    val chunkOverlapWords: Int = 40,
    val topK: Int = 4,
    val similarityThreshold: Float = 0.20f,
    val geminiModel: String = "gemini-3.5-flash"
)
