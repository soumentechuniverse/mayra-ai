package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ChatRepository
import com.example.domain.model.AiModelConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val _inputText = MutableStateFlow("")
    private val _selectedModel = MutableStateFlow(AiModelConfig.AvailableModels.first())
    private val _isDarkTheme = MutableStateFlow(true)
    private val _isSettingsOpen = MutableStateFlow(false)
    private val _isHistoryOpen = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private val _bannerError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ChatUiState> = combine(
        repository.activeConversation,
        repository.messages,
        repository.conversations,
        repository.isGenerating,
        _inputText,
        _selectedModel,
        _isDarkTheme,
        _isSettingsOpen,
        _isHistoryOpen,
        _snackbarMessage,
        _bannerError
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        ChatUiState(
            conversation = args[0] as? com.example.domain.model.Conversation,
            messages = args[1] as? List<com.example.domain.model.ChatMessage> ?: emptyList(),
            allConversations = args[2] as? List<com.example.domain.model.Conversation> ?: emptyList(),
            isGenerating = args[3] as? Boolean ?: false,
            inputText = args[4] as? String ?: "",
            selectedModel = args[5] as? AiModelConfig ?: AiModelConfig.AvailableModels.first(),
            isDarkTheme = args[6] as? Boolean ?: true,
            isSettingsOpen = args[7] as? Boolean ?: false,
            isHistoryOpen = args[8] as? Boolean ?: false,
            snackbarMessage = args[9] as? String,
            bannerError = args[10] as? String
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState()
    )

    fun onEvent(event: ChatUiEvent) {
        when (event) {
            is ChatUiEvent.InputTextChanged -> {
                _inputText.value = event.newText
            }

            ChatUiEvent.SendClicked -> {
                val currentText = _inputText.value.trim()
                if (currentText.isEmpty()) {
                    _snackbarMessage.value = "Please enter a message."
                    return
                }
                _inputText.value = ""
                _bannerError.value = null
                viewModelScope.launch {
                    val result = repository.sendMessage(currentText, _selectedModel.value)
                    if (result.isFailure) {
                        _bannerError.value = result.exceptionOrNull()?.localizedMessage
                            ?: "Mayra AI service request failed. Tap retry to reconnect."
                    }
                }
            }

            is ChatUiEvent.SuggestionClicked -> {
                _inputText.value = ""
                _bannerError.value = null
                viewModelScope.launch {
                    val result = repository.sendMessage(event.prompt, _selectedModel.value)
                    if (result.isFailure) {
                        _bannerError.value = result.exceptionOrNull()?.localizedMessage
                            ?: "Unable to process prompt. Tap retry."
                    }
                }
            }

            ChatUiEvent.RetryLastFailed -> {
                _bannerError.value = null
                viewModelScope.launch {
                    val result = repository.retryLastFailed(_selectedModel.value)
                    if (result.isFailure) {
                        _bannerError.value = result.exceptionOrNull()?.localizedMessage
                            ?: "Retry attempt failed."
                    }
                }
            }

            ChatUiEvent.NewChatClicked -> {
                viewModelScope.launch {
                    repository.startNewConversation()
                    _isHistoryOpen.value = false
                    _bannerError.value = null
                    _inputText.value = ""
                }
            }

            is ChatUiEvent.SelectConversation -> {
                viewModelScope.launch {
                    repository.selectConversation(event.conversationId)
                    _isHistoryOpen.value = false
                }
            }

            is ChatUiEvent.DeleteConversation -> {
                viewModelScope.launch {
                    repository.deleteConversation(event.conversationId)
                }
            }

            is ChatUiEvent.ModelSelected -> {
                _selectedModel.value = event.model
                _snackbarMessage.value = "Switched to ${event.model.displayName}"
            }

            is ChatUiEvent.TemperatureChanged -> {
                _selectedModel.value = _selectedModel.value.copy(temperature = event.temperature)
            }

            ChatUiEvent.OpenSettings -> _isSettingsOpen.value = true
            ChatUiEvent.CloseSettings -> _isSettingsOpen.value = false

            ChatUiEvent.OpenHistory -> _isHistoryOpen.value = true
            ChatUiEvent.CloseHistory -> _isHistoryOpen.value = false

            ChatUiEvent.ToggleTheme -> _isDarkTheme.value = !_isDarkTheme.value

            ChatUiEvent.AttachmentPlaceholderClicked -> {
                _snackbarMessage.value = "Document & image attachments will be enabled in the upcoming update."
            }

            ChatUiEvent.VoicePlaceholderClicked -> {
                _snackbarMessage.value = "High-fidelity voice conversation will be enabled in the upcoming update."
            }

            ChatUiEvent.ClearCurrentChat -> {
                viewModelScope.launch {
                    repository.clearMessages()
                    _snackbarMessage.value = "Conversation cleared."
                }
            }

            is ChatUiEvent.DismissSnackbar -> _snackbarMessage.value = null
            ChatUiEvent.DismissBannerError -> _bannerError.value = null
            ChatUiEvent.StopGeneration -> {
                viewModelScope.launch {
                    repository.cancelGeneration()
                }
            }
        }
    }

    companion object {
        fun provideFactory(repository: ChatRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(repository) as T
                }
            }
    }
}
