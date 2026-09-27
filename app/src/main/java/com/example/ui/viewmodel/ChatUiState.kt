package com.example.ui.viewmodel

import com.example.domain.model.AiModelConfig
import com.example.domain.model.ChatMessage
import com.example.domain.model.Conversation

data class ChatUiState(
    val conversation: Conversation? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val selectedModel: AiModelConfig = AiModelConfig.AvailableModels.first(),
    val availableModels: List<AiModelConfig> = AiModelConfig.AvailableModels,
    val allConversations: List<Conversation> = emptyList(),
    val isDarkTheme: Boolean = true,
    val isSettingsOpen: Boolean = false,
    val isHistoryOpen: Boolean = false,
    val snackbarMessage: String? = null,
    val bannerError: String? = null
)
