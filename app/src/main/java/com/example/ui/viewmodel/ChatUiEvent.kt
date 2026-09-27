package com.example.ui.viewmodel

import com.example.domain.model.AiModelConfig

sealed interface ChatUiEvent {
    data class InputTextChanged(val newText: String) : ChatUiEvent
    data object SendClicked : ChatUiEvent
    data class SuggestionClicked(val prompt: String) : ChatUiEvent
    data object RetryLastFailed : ChatUiEvent
    data object NewChatClicked : ChatUiEvent
    data class SelectConversation(val conversationId: String) : ChatUiEvent
    data class DeleteConversation(val conversationId: String) : ChatUiEvent
    data class ModelSelected(val model: AiModelConfig) : ChatUiEvent
    data class TemperatureChanged(val temperature: Float) : ChatUiEvent
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
}
