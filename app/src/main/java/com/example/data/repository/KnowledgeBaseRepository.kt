package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.db.AppDatabase
import com.example.data.db.ChatMessageEntity
import com.example.data.db.DocumentChunkEntity
import com.example.data.db.DocumentEntity
import com.example.data.model.DocumentType
import com.example.data.model.ParsedDocument
import com.example.data.model.RagSettings
import com.example.data.model.RetrievedChunk
import com.example.data.model.SourceCitation
import com.example.data.model.StorageStats
import com.example.data.parser.DocumentParserFactory
import com.example.data.preferences.RagSettingsRepository
import com.example.data.remote.GeminiRagService
import com.example.data.vector.TextChunker
import com.example.data.vector.VectorEngine
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File

class KnowledgeBaseRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsRepository: RagSettingsRepository,
    private val parserFactory: DocumentParserFactory = DocumentParserFactory(),
    private val textChunker: TextChunker = TextChunker(),
    private val vectorEngine: VectorEngine = VectorEngine(),
    private val geminiService: GeminiRagService = GeminiRagService()
) {
    private val documentDao = database.documentDao()
    private val chunkDao = database.documentChunkDao()
    private val chatDao = database.chatMessageDao()

    private val moshi = Moshi.Builder().build()
    private val citationListType = Types.newParameterizedType(List::class.java, SourceCitation::class.java)
    private val citationAdapter = moshi.adapter<List<SourceCitation>>(citationListType)

    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    val settings: Flow<RagSettings> = settingsRepository.settings

    fun getChatMessages(sessionId: String = "default"): Flow<List<ChatMessageEntity>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    /**
     * Import a document from a picked URI (PDF, DOCX, TXT, etc.)
     */
    suspend fun importDocument(uri: Uri, fileName: String, mimeType: String?): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val parsed = parserFactory.parseUri(context, uri, fileName, mimeType)
            val docId = saveAndIndexParsedDoc(parsed, uri.toString())
            Result.success(docId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Import a manual note or raw text entry
     */
    suspend fun importRawText(title: String, text: String, type: DocumentType = DocumentType.MANUAL): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val parsed = parserFactory.parseRawText(title, text, type)
            val docId = saveAndIndexParsedDoc(parsed, null)
            Result.success(docId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun saveAndIndexParsedDoc(parsed: ParsedDocument, uriString: String?): Long {
        val currentSettings = settingsRepository.settings.value
        val words = parsed.text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val previewSummary = if (parsed.text.length > 180) parsed.text.take(180) + "..." else parsed.text

        val docEntity = DocumentEntity(
            title = parsed.title.ifBlank { "Untitled Document" },
            fileType = parsed.type.name,
            fileUri = uriString,
            fileSizeBytes = parsed.fileSizeBytes,
            wordCount = words.size,
            chunkCount = 0,
            status = "INDEXING",
            summary = previewSummary
        )

        val docId = documentDao.insertDocument(docEntity)

        if (currentSettings.autoIndexOnUpload) {
            indexDocumentInternal(docId, parsed.title, parsed.type.name, parsed.text, currentSettings)
        } else {
            documentDao.updateDocument(docEntity.copy(id = docId, status = "READY"))
        }

        return docId
    }

    /**
     * Index or Re-index a specific document
     */
    suspend fun reindexDocument(documentId: Long): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val doc = documentDao.getDocumentById(documentId)
                ?: return@withContext Result.failure(Exception("Document not found"))
            val currentSettings = settingsRepository.settings.value

            // Fetch existing chunks text or re-read if URI available
            val existingChunks = chunkDao.getChunksForDocumentSync(documentId)
            val fullText = if (existingChunks.isNotEmpty()) {
                existingChunks.joinToString("\n\n") { it.text }
            } else if (doc.fileUri != null) {
                val parsed = parserFactory.parseUri(context, Uri.parse(doc.fileUri), doc.title, null)
                parsed.text
            } else {
                doc.summary
            }

            val chunkCount = indexDocumentInternal(documentId, doc.title, doc.fileType, fullText, currentSettings)
            Result.success(chunkCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun indexDocumentInternal(
        docId: Long,
        title: String,
        fileType: String,
        text: String,
        settings: RagSettings
    ): Int {
        // Delete old chunks first
        chunkDao.deleteChunksByDocumentId(docId)

        val rawChunks = textChunker.chunkText(
            text = text,
            chunkSizeWords = settings.chunkSizeWords,
            chunkOverlapWords = settings.chunkOverlapWords
        )

        val chunkEntities = rawChunks.map { raw ->
            val embedding = vectorEngine.computeEmbedding(raw.text)
            val vectorJson = vectorEngine.vectorToJson(embedding)
            DocumentChunkEntity(
                documentId = docId,
                documentTitle = title,
                fileType = fileType,
                chunkIndex = raw.index,
                text = raw.text,
                vectorJson = vectorJson,
                wordCount = raw.wordCount
            )
        }

        chunkDao.insertChunks(chunkEntities)

        val totalWords = rawChunks.sumOf { it.wordCount }
        val doc = documentDao.getDocumentById(docId)
        if (doc != null) {
            documentDao.updateDocument(
                doc.copy(
                    chunkCount = chunkEntities.size,
                    wordCount = totalWords,
                    status = "INDEXED",
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        return chunkEntities.size
    }

    /**
     * Delete document and all associated chunks
     */
    suspend fun deleteDocument(documentId: Long) = withContext(Dispatchers.IO) {
        chunkDao.deleteChunksByDocumentId(documentId)
        documentDao.deleteDocumentById(documentId)
    }

    /**
     * Rebuild the entire vector index for all documents
     */
    suspend fun rebuildAllIndexes(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val allDocs = documentDao.getAllDocuments().firstOrNull() ?: emptyList()
            var totalChunksCreated = 0
            val currentSettings = settingsRepository.settings.value

            for (doc in allDocs) {
                val chunks = chunkDao.getChunksForDocumentSync(doc.id)
                val fullText = if (chunks.isNotEmpty()) {
                    chunks.joinToString("\n\n") { it.text }
                } else if (doc.fileUri != null) {
                    try {
                        val parsed = parserFactory.parseUri(context, Uri.parse(doc.fileUri), doc.title, null)
                        parsed.text
                    } catch (e: Exception) {
                        doc.summary
                    }
                } else {
                    doc.summary
                }
                totalChunksCreated += indexDocumentInternal(doc.id, doc.title, doc.fileType, fullText, currentSettings)
            }
            Result.success(totalChunksCreated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Vector Search across all indexed document chunks
     */
    suspend fun searchVectorIndex(
        query: String,
        topK: Int = 4,
        similarityThreshold: Float = 0.20f
    ): List<RetrievedChunk> = withContext(Dispatchers.IO) {
        val allChunks = chunkDao.getAllChunks()
        if (allChunks.isEmpty() || query.isBlank()) return@withContext emptyList()

        val chunkTriples = allChunks.map {
            Triple(it.id, it.documentId, Pair(it.text, it.vectorJson))
        }
        val chunkMeta = allChunks.associate {
            it.id to Triple(it.documentTitle, it.fileType, Pair(it.chunkIndex, it.wordCount))
        }

        vectorEngine.rankChunks(
            query = query,
            chunks = chunkTriples,
            chunkMeta = chunkMeta,
            topK = topK,
            similarityThreshold = similarityThreshold
        )
    }

    /**
     * Primary Ask Question pipeline (Chatbot grounding + RAG response)
     */
    suspend fun askQuestion(
        question: String,
        sessionId: String = "default"
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmedQ = question.trim()
        if (trimmedQ.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Question cannot be empty."))

        val currentSettings = settingsRepository.settings.value
        val effectiveApiKey = settingsRepository.getEffectiveApiKey()
        val isOfflineMode = currentSettings.offlineModeOnly || effectiveApiKey.isBlank()

        // 1. Save user message
        val userMsg = ChatMessageEntity(
            sessionId = sessionId,
            sender = "USER",
            content = trimmedQ,
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(userMsg)

        // 2. Perform local vector search retrieval
        val retrieved = searchVectorIndex(
            query = trimmedQ,
            topK = currentSettings.topK,
            similarityThreshold = currentSettings.similarityThreshold
        )

        // 3. Generate response
        val botResponseText: String
        val isOfflineUsed: Boolean

        if (!isOfflineMode) {
            val geminiResult = geminiService.generateRagResponse(
                query = trimmedQ,
                retrievedChunks = retrieved,
                apiKey = effectiveApiKey,
                settings = currentSettings
            )

            if (geminiResult.isSuccess) {
                botResponseText = geminiResult.getOrNull() ?: ""
                isOfflineUsed = false
            } else {
                // Fallback to offline synthesis if online call failed (e.g. rate limit, no internet)
                val offlineReply = geminiService.generateOfflineExtractiveResponse(trimmedQ, retrieved)
                val errorNotice = "*(Note: Switched to offline retrieval due to network/API error: ${geminiResult.exceptionOrNull()?.message?.take(80)})*\n\n"
                botResponseText = errorNotice + offlineReply
                isOfflineUsed = true
            }
        } else {
            botResponseText = geminiService.generateOfflineExtractiveResponse(trimmedQ, retrieved)
            isOfflineUsed = true
        }

        // 4. Save citations JSON
        val citations = retrieved.map {
            SourceCitation(
                chunkId = it.chunkId,
                documentId = it.documentId,
                documentTitle = it.documentTitle,
                fileType = it.fileType,
                chunkIndex = it.chunkIndex,
                similarityScore = it.similarityScore,
                previewText = if (it.text.length > 150) it.text.take(150) + "..." else it.text
            )
        }
        val citationsJson = if (citations.isNotEmpty()) citationAdapter.toJson(citations) else null

        // 5. Save assistant message
        val botMsg = ChatMessageEntity(
            sessionId = sessionId,
            sender = "BOT",
            content = botResponseText,
            timestamp = System.currentTimeMillis(),
            referencedChunksJson = citationsJson,
            isOffline = isOfflineUsed
        )
        chatDao.insertMessage(botMsg)

        Result.success(botResponseText)
    }

    suspend fun clearChat(sessionId: String = "default") = withContext(Dispatchers.IO) {
        chatDao.deleteSessionMessages(sessionId)
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        chatDao.deleteAllMessages()
        chunkDao.deleteAllChunks()
        documentDao.deleteAllDocuments()
    }

    /**
     * Storage statistics breakdown
     */
    suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        val docs = documentDao.getAllDocuments().firstOrNull() ?: emptyList()
        val chunks = chunkDao.getAllChunks()
        val totalBytes = docs.sumOf { it.fileSizeBytes }
        val vectorIndexBytes = chunks.sumOf { it.vectorJson.toByteArray().size.toLong() }
        val totalWords = docs.sumOf { it.wordCount }

        StorageStats(
            totalDocuments = docs.size,
            totalChunks = chunks.size,
            totalWords = totalWords,
            totalSizeBytes = totalBytes,
            vectorIndexSizeBytes = vectorIndexBytes
        )
    }

    /**
     * Load rich pre-configured sample documents for instant testing
     */
    suspend fun loadSampleKnowledgeBase() = withContext(Dispatchers.IO) {
        val sample1 = """
            # DocuMind System Architecture & Vector RAG Overview
            DocuMind is an on-device Retrieval-Augmented Generation (RAG) system engineered for high privacy and speed.
            All user documents (PDF, DOCX, TXT) are parsed directly in local storage and partitioned into overlapping semantic chunks.
            
            Key Components:
            1. Document Ingestion Pipeline: Streams text using pure offline parsers for DOCX (Word XML pull parser), PDF (Stream decompressor and font extractor), and standard plain text.
            2. Local Vector Engine: Employs 256-dimensional subword and character n-gram hashing with TF-IDF weighting and L2 normalization.
            3. Cosine Similarity Index: Quickly scores all document chunks on device in real time with hybrid BM25 keyword boosting.
            4. Generation Pipeline: Passes top-K retrieved sources to Gemini 3.5 Flash for natural language grounding or synthesizes responses offline using local extractive logic.
        """.trimIndent()

        val sample2 = """
            # Employee Handbook: Remote Work & Data Privacy Policies
            Policy Ref: HR-2026-PRIV
            
            1. Data Storage & Privacy:
            Employees must ensure sensitive client records are stored exclusively on encrypted storage. Cloud sync for unapproved platforms is strictly prohibited.
            
            2. Working Hours & Availability:
            Core collaboration hours are 10:00 AM to 3:00 PM EST. Flexible scheduling is permitted outside core hours provided weekly targets of 40 hours are met.
            
            3. Expense Reimbursement:
            Home office equipment, high-speed internet, and ergonomic furniture up to $500 annually can be reimbursed through the Finance portal within 30 days of purchase.
            
            4. Vacation & Time Off:
            Full-time employees receive 20 days paid annual leave plus 10 public holidays. Leave requests longer than 3 consecutive days require 2 weeks prior notice.
        """.trimIndent()

        val sample3 = """
            # Project Apollo Technical Specification
            Status: Active Development
            Lead Architect: Dr. Elena Vance
            
            Objective:
            Design a resilient, offline-capable telemetry processor for environmental sensor networks.
            
            Technical Stack:
            - Language: Kotlin 2.2 with Jetpack Compose
            - Database: SQLite with Room persistence
            - Network Protocol: MQTT over TLS v1.3 with automated retry backoff
            - Battery Optimization: Target average power consumption below 45mW in standby mode
            - Deployment Date: Q4 2026
        """.trimIndent()

        importRawText("DocuMind Architecture Guide.txt", sample1, DocumentType.TXT)
        importRawText("Company Privacy & Remote Policy.docx", sample2, DocumentType.DOCX)
        importRawText("Project Apollo Technical Spec.pdf", sample3, DocumentType.PDF)
    }

    suspend fun ensureInitialDataLoaded() = withContext(Dispatchers.IO) {
        val count = documentDao.getDocumentCount()
        if (count == 0) {
            loadSampleKnowledgeBase()
        }
    }
}
