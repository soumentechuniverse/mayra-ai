package com.example.ui.viewmodel

import com.example.domain.model.AiModelConfig
import com.example.domain.model.Conversation
import com.example.domain.model.MemoryCategory

sealed interface ChatUiEvent {
    data class InputTextChanged(val newText: String) : ChatUiEvent
    data object SendClicked : ChatUiEvent
    data class SuggestionClicked(val prompt: String) : ChatUiEvent
    data object RetryLastFailed : ChatUiEvent
    data object NewChatClicked : ChatUiEvent
    data class SelectConversation(val conversationId: String) : ChatUiEvent
    data class DeleteConversation(val conversationId: String) : ChatUiEvent
    data class RenameConversation(val conversationId: String, val newTitle: String) : ChatUiEvent
    data class TogglePinConversation(val conversationId: String) : ChatUiEvent
    data class ToggleArchiveConversation(val conversationId: String) : ChatUiEvent
    data class RequestRenameConversation(val conversation: Conversation?) : ChatUiEvent
    data class RequestDeleteConversation(val conversation: Conversation?) : ChatUiEvent
    data object ConfirmDeleteConversation : ChatUiEvent
    data object DismissDeleteConversation : ChatUiEvent
    data class HistorySearchQueryChanged(val query: String) : ChatUiEvent
    data class ToggleHistoryArchivedFilter(val showArchived: Boolean) : ChatUiEvent

    // Memory Events
    data class ToggleMemoryEnabled(val enabled: Boolean) : ChatUiEvent
    data object OpenManageMemory : ChatUiEvent
    data object CloseManageMemory : ChatUiEvent
    data class AddMemory(val content: String, val category: MemoryCategory) : ChatUiEvent
    data class ToggleMemoryItem(val memoryId: String, val enabled: Boolean) : ChatUiEvent
    data class DeleteMemoryItem(val memoryId: String) : ChatUiEvent
    data object RequestClearMemories : ChatUiEvent
    data object ConfirmClearMemories : ChatUiEvent
    data object DismissClearMemories : ChatUiEvent

    data class ModelSelected(val model: AiModelConfig) : ChatUiEvent
    data class TemperatureChanged(val temperature: Float) : ChatUiEvent
    data class SearchModeChanged(val mode: com.example.domain.model.SearchMode) : ChatUiEvent
    data object OpenSettings : ChatUiEvent
    data object CloseSettings : ChatUiEvent
    data object OpenHistory : ChatUiEvent
    data object CloseHistory : ChatUiEvent
    data object ToggleTheme : ChatUiEvent
    data object AttachmentPlaceholderClicked : ChatUiEvent
    data object OpenAttachmentPicker : ChatUiEvent
    data object CloseAttachmentPicker : ChatUiEvent
    data class AttachmentsSelected(val attachments: List<com.example.domain.model.Attachment>) : ChatUiEvent
    data class RemovePendingAttachment(val attachmentId: String) : ChatUiEvent
    data class AttachmentError(val errorMessage: String) : ChatUiEvent
    data object VoicePlaceholderClicked : ChatUiEvent
    data object ClearCurrentChat : ChatUiEvent
    data class DismissSnackbar(val message: String? = null) : ChatUiEvent
    data object DismissBannerError : ChatUiEvent
    data object StopGeneration : ChatUiEvent

    // Voice Interaction Events
    data object StartVoiceInput : ChatUiEvent
    data object StopVoiceInput : ChatUiEvent
    data object CancelVoiceInput : ChatUiEvent
    data object VoicePermissionDenied : ChatUiEvent
    data class SpeakMessage(val messageId: String, val text: String) : ChatUiEvent
    data object PauseSpeech : ChatUiEvent
    data object ResumeSpeech : ChatUiEvent
    data object StopSpeech : ChatUiEvent
    data class VoiceInputLanguageChanged(val language: com.example.domain.model.VoiceLanguage) : ChatUiEvent
    data class VoiceAutoSpeakToggled(val enabled: Boolean) : ChatUiEvent
    data class VoiceOutputLanguageChanged(val language: com.example.domain.model.VoiceLanguage) : ChatUiEvent

    // App Update Events
    data object CheckForAppUpdate : ChatUiEvent
    data class UpdateSourceUrlChanged(val url: String) : ChatUiEvent
    data object ResetUpdateSourceUrl : ChatUiEvent
    data object ToggleUpdateSourceConfig : ChatUiEvent
    data class DownloadAppUpdate(val context: android.content.Context, val url: String) : ChatUiEvent
    data object DismissUpdateDialog : ChatUiEvent
}
