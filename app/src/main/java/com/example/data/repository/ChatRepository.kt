package com.example.data.repository

import com.example.domain.model.AiModelConfig
import com.example.domain.model.Attachment
import com.example.domain.model.ChatMessage
import com.example.domain.model.Conversation
import com.example.domain.model.SearchPhase
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {
    val activeConversation: StateFlow<Conversation?>
    val messages: StateFlow<List<ChatMessage>>
    val conversations: StateFlow<List<Conversation>>
    val isGenerating: StateFlow<Boolean>
    val searchPhase: StateFlow<SearchPhase>

    suspend fun startNewConversation(title: String = "New Chat"): Conversation
    suspend fun selectConversation(conversationId: String)
    suspend fun sendMessage(
        content: String,
        config: AiModelConfig,
        attachments: List<Attachment> = emptyList()
    ): Result<ChatMessage>
    suspend fun retryLastFailed(config: AiModelConfig): Result<ChatMessage>
    suspend fun clearMessages()
    suspend fun deleteConversation(conversationId: String)
    suspend fun cancelGeneration()
}
