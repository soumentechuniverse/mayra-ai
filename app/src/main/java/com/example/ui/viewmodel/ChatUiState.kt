package com.example.ui.viewmodel

import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.ui.components.AttachmentInfo

data class ChatUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val archivedConversations: List<ConversationEntity> = emptyList(),
    val currentConversationId: String? = null,
    val currentConversationTitle: String = "Mayra AI",
    val messages: List<MessageEntity> = emptyList(),
    val inputText: String = "",
    val attachedFile: AttachmentInfo? = null,
    val isGenerating: Boolean = false,
    val isThinking: Boolean = false,
    val errorMessage: String? = null,

    // Settings
    val isSettingsOpen: Boolean = false
)
