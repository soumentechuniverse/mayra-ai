package com.example.domain.model

import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class MessageStatus {
    SENDING,
    SENT,
    ERROR
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val errorMessage: String? = null,
    val attachments: List<AttachmentMetadata> = emptyList(),
    val searchSources: List<SearchSource> = emptyList(),

    // AI-generated image data.
    // Base64 contains the generated image bytes.
    val generatedImageBase64: String? = null,
    val generatedImageMimeType: String? = null
)
