package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SENT", // "SENDING", "SENT", "ERROR"
    val attachmentUri: String? = null,
    val attachmentMimeType: String? = null,
    val attachmentName: String? = null,
    val imageUrl: String? = null,
    val sourcesJson: String? = null, // JSON list of citations/sources
    val isGeneratedImage: Boolean = false
)
