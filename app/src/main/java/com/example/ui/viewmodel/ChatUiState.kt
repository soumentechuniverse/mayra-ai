package com.example.ui.viewmodel

import com.example.domain.model.AiModelConfig
import com.example.domain.model.Attachment
import com.example.domain.model.ChatMessage
import com.example.domain.model.Conversation
import com.example.domain.model.SearchMode
import com.example.domain.model.SearchPhase

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
    val isAttachmentPickerOpen: Boolean = false,
    val pendingAttachments: List<Attachment> = emptyList(),
    val searchPhase: SearchPhase = SearchPhase.IDLE,
    val searchMode: SearchMode = SearchMode.AUTO,
    val snackbarMessage: String? = null,
    val bannerError: String? = null
)
