package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String = "default",
    val sender: String, // "USER" or "BOT"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val referencedChunksJson: String? = null,
    val isOffline: Boolean = false
)
