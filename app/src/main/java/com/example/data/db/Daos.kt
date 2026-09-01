package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("DELETE FROM documents")
    suspend fun deleteAllDocuments()

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getDocumentCount(): Int
}

@Dao
interface DocumentChunkDao {
    @Query("SELECT * FROM document_chunks WHERE documentId = :docId ORDER BY chunkIndex ASC")
    fun getChunksForDocument(docId: Long): Flow<List<DocumentChunkEntity>>

    @Query("SELECT * FROM document_chunks WHERE documentId = :docId ORDER BY chunkIndex ASC")
    suspend fun getChunksForDocumentSync(docId: Long): List<DocumentChunkEntity>

    @Query("SELECT * FROM document_chunks")
    suspend fun getAllChunks(): List<DocumentChunkEntity>

    @Query("SELECT * FROM document_chunks")
    fun getAllChunksFlow(): Flow<List<DocumentChunkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DocumentChunkEntity>)

    @Query("DELETE FROM document_chunks WHERE documentId = :docId")
    suspend fun deleteChunksByDocumentId(docId: Long)

    @Query("DELETE FROM document_chunks")
    suspend fun deleteAllChunks()

    @Query("SELECT COUNT(*) FROM document_chunks")
    suspend fun getChunkCount(): Int
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteSessionMessages(sessionId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()
}
