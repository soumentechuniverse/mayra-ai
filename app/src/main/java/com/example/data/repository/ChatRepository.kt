package com.example.data.repository

import com.example.domain.model.AiModelConfig
import com.example.domain.model.Attachment
import com.example.domain.model.ChatMessage
import com.example.domain.model.Conversation
import com.example.domain.model.MemoryCategory
import com.example.domain.model.MemoryItem
import com.example.domain.model.SearchPhase
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {
    val activeConversation: StateFlow<Conversation?>
    val messages: StateFlow<List<ChatMessage>>
    val conversations: StateFlow<List<Conversation>>
    val memories: StateFlow<List<MemoryItem>>
    val isMemoryEnabled: StateFlow<Boolean>
    val isGenerating: StateFlow<Boolean>
    val searchPhase: StateFlow<SearchPhase>

    suspend fun startNewConversation(title: String = "New Chat"): Conversation
    suspend fun selectConversation(conversationId: String)
    suspend fun renameConversation(conversationId: String, newTitle: String)
    suspend fun togglePinConversation(conversationId: String)
    suspend fun toggleArchiveConversation(conversationId: String)
    suspend fun sendMessage(
        content: String,
        config: AiModelConfig,
        attachments: List<Attachment> = emptyList()
    ): Result<ChatMessage>
    suspend fun retryLastFailed(config: AiModelConfig): Result<ChatMessage>
    suspend fun clearMessages()
    suspend fun deleteConversation(conversationId: String)
    suspend fun cancelGeneration()

    // Memory Management
    fun setMemoryEnabled(enabled: Boolean)
    suspend fun saveMemory(content: String, category: MemoryCategory = MemoryCategory.OTHER): MemoryItem
    suspend fun toggleMemoryItemEnabled(memoryId: String, enabled: Boolean)
    suspend fun deleteMemory(memoryId: String)
    suspend fun clearAllMemories()

    // Search
    suspend fun searchConversations(query: String): List<Conversation>
}
