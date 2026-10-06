package com.example.data.repository

import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.remote.model.WebSourceCitation
import kotlinx.coroutines.flow.Flow

sealed class StreamEvent {
    data class TextChunk(val text: String) : StreamEvent()
    data class SourcesDiscovered(val sources: List<WebSourceCitation>) : StreamEvent()
    data class Completed(val fullText: String, val sources: List<WebSourceCitation>) : StreamEvent()
    data class Error(val message: String, val isRetryable: Boolean) : StreamEvent()
}

interface ChatRepository {
    fun getConversations(): Flow<List<ConversationEntity>>
    fun getArchivedConversations(): Flow<List<ConversationEntity>>
    fun searchConversations(query: String): Flow<List<ConversationEntity>>
    suspend fun getConversationById(id: String): ConversationEntity?
    suspend fun createConversation(title: String): ConversationEntity
    suspend fun updateConversationTitle(id: String, newTitle: String)
    suspend fun togglePinConversation(id: String, isPinned: Boolean)
    suspend fun toggleArchiveConversation(id: String, isArchived: Boolean)
    suspend fun deleteConversation(id: String)

    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>
    suspend fun deleteMessage(messageId: String)

    suspend fun sendMessage(
        conversationId: String,
        userPrompt: String,
        attachmentBytes: ByteArray? = null,
        attachmentMimeType: String? = null,
        attachmentName: String? = null,
        attachmentUriString: String? = null
    ): Flow<StreamEvent>

    suspend fun retryMessage(
        conversationId: String,
        failedMessageId: String
    ): Flow<StreamEvent>
}
