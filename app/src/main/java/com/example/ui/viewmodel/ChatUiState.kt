package com.example.ui.viewmodel

import com.example.domain.model.AiModelConfig
import com.example.domain.model.Attachment
import com.example.domain.model.ChatMessage
import com.example.domain.model.Conversation
import com.example.domain.model.MemoryItem
import com.example.domain.model.SearchMode
import com.example.domain.model.SearchPhase
import com.example.domain.model.VoiceSettings
import com.example.domain.model.VoiceState

data class ChatUiState(
    val conversation: Conversation? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val selectedModel: AiModelConfig = AiModelConfig.AvailableModels.first(),
    val availableModels: List<AiModelConfig> = AiModelConfig.AvailableModels,
    val allConversations: List<Conversation> = emptyList(),
    val filteredConversations: List<Conversation> = emptyList(),
    val historySearchQuery: String = "",
    val showArchivedInHistory: Boolean = false,
    val renameConversationDialogState: Conversation? = null,
    val deleteConversationDialogState: Conversation? = null,
    val memories: List<MemoryItem> = emptyList(),
    val isMemoryEnabled: Boolean = true,
    val isManageMemoryOpen: Boolean = false,
    val clearMemoriesConfirmationOpen: Boolean = false,
    val isDarkTheme: Boolean = true,
    val isSettingsOpen: Boolean = false,
    val isHistoryOpen: Boolean = false,
    val isAttachmentPickerOpen: Boolean = false,
    val pendingAttachments: List<Attachment> = emptyList(),
    val searchPhase: SearchPhase = SearchPhase.IDLE,
    val searchMode: SearchMode = SearchMode.AUTO,
    val voiceState: VoiceState = VoiceState.Idle,
    val voiceSettings: VoiceSettings = VoiceSettings(),
    val snackbarMessage: String? = null,
    val bannerError: String? = null,
    val isCheckingUpdate: Boolean = false,
    val updateInfo: com.example.data.service.AppUpdateInfo? = null,
    val updateStatusMessage: String? = null,
    val updateSourceUrl: String = com.example.data.local.AppUpdatePreferences.DEFAULT_UPDATE_URL,
    val isUpdateSourceConfigOpen: Boolean = false
)
