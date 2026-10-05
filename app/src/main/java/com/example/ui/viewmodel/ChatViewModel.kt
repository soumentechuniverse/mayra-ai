package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.VoicePreferences
import com.example.data.repository.ChatRepository
import com.example.domain.model.AiModelConfig
import com.example.domain.model.ChatMessage
import com.example.domain.model.Conversation
import com.example.domain.model.MemoryItem
import com.example.domain.model.MessageRole
import com.example.domain.model.MessageStatus
import com.example.domain.model.SearchMode
import com.example.domain.model.SearchPhase
import com.example.domain.model.VoiceSettings
import com.example.domain.model.VoiceState
import com.example.domain.service.SpeechRecognizerService
import com.example.domain.service.TextToSpeechService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
    private val speechRecognizerService: SpeechRecognizerService? = null,
    private val textToSpeechService: TextToSpeechService? = null,
    private val voicePreferences: VoicePreferences? = null,
    private val appUpdatePreferences: com.example.data.local.AppUpdatePreferences? = null,
    private val appUpdateService: com.example.data.service.AppUpdateService = com.example.data.service.AppUpdateService()
) : ViewModel() {

    private val _inputText = MutableStateFlow("")
    private val _selectedModel = MutableStateFlow(AiModelConfig.AvailableModels.first())
    private val _searchMode = MutableStateFlow(SearchMode.AUTO)
    private val _isDarkTheme = MutableStateFlow(true)
    private val _isSettingsOpen = MutableStateFlow(false)
    private val _isHistoryOpen = MutableStateFlow(false)
    private val _isAttachmentPickerOpen = MutableStateFlow(false)
    private val _pendingAttachments = MutableStateFlow<List<com.example.domain.model.Attachment>>(emptyList())
    private val _isProcessingAttachment = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private val _bannerError = MutableStateFlow<String?>(null)

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    private val _voiceSettings = MutableStateFlow(voicePreferences?.settings?.value ?: VoiceSettings())

    // Step 6: Advanced Conversation Management & Memory States
    private val _historySearchQuery = MutableStateFlow("")
    private val _historyDateFilter = MutableStateFlow(com.example.util.HistoryDateFilter.ALL)
    private val _historyCustomDateEpoch = MutableStateFlow<Long?>(null)
    private val _showArchivedInHistory = MutableStateFlow(false)
    private val _renameConversationDialogState = MutableStateFlow<Conversation?>(null)
    private val _deleteConversationDialogState = MutableStateFlow<Conversation?>(null)
    private val _isManageMemoryOpen = MutableStateFlow(false)
    private val _clearMemoriesConfirmationOpen = MutableStateFlow(false)

    // App Update States
    private val _isCheckingUpdate = MutableStateFlow(false)
    private val _updateInfo = MutableStateFlow<com.example.data.service.AppUpdateInfo?>(null)
    private val _updateStatusMessage = MutableStateFlow<String?>(null)
    private val _updateSourceUrl = MutableStateFlow(
        appUpdatePreferences?.getUpdateUrl() ?: com.example.data.local.AppUpdatePreferences.DEFAULT_UPDATE_URL
    )
    private val _isUpdateSourceConfigOpen = MutableStateFlow(false)

    private var lastAutoSpokenMessageId: String? = null

    init {
        // Collect update preferences if present
        if (appUpdatePreferences != null) {
            viewModelScope.launch {
                appUpdatePreferences.updateSourceUrl.collect { url ->
                    _updateSourceUrl.value = url
                }
            }
        }
        // Collect voice preferences
        if (voicePreferences != null) {
            viewModelScope.launch {
                voicePreferences.settings.collect { s ->
                    _voiceSettings.value = s
                }
            }
        }

        // Collect Speech Recognizer state
        if (speechRecognizerService != null) {
            viewModelScope.launch {
                speechRecognizerService.state.collect { s ->
                    if (s !is VoiceState.Idle) {
                        _voiceState.value = s
                    } else if (_voiceState.value !is VoiceState.Speaking && _voiceState.value !is VoiceState.Error) {
                        _voiceState.value = VoiceState.Idle
                    }
                }
            }
        }

        // Collect Text-To-Speech state
        if (textToSpeechService != null) {
            viewModelScope.launch {
                textToSpeechService.state.collect { s ->
                    if (s is VoiceState.Speaking) {
                        _voiceState.value = s
                    } else if (_voiceState.value is VoiceState.Speaking) {
                        _voiceState.value = VoiceState.Idle
                    }
                }
            }
        }

        // Auto-speak completed assistant responses if enabled in settings
        viewModelScope.launch {
            repository.messages.collect { msgList ->
                val lastMsg = msgList.lastOrNull()
                if (lastMsg != null &&
                    lastMsg.role == MessageRole.ASSISTANT &&
                    lastMsg.status == MessageStatus.SENT &&
                    lastMsg.content.isNotBlank() &&
                    lastMsg.id != lastAutoSpokenMessageId
                ) {
                    if (_voiceSettings.value.autoSpeakOutput && !repository.isGenerating.value) {
                        lastAutoSpokenMessageId = lastMsg.id
                        textToSpeechService?.speak(
                            messageId = lastMsg.id,
                            text = lastMsg.content,
                            language = _voiceSettings.value.outputLanguage
                        )
                    }
                }
            }
        }
    }

    val uiState: StateFlow<ChatUiState> = combine(
        combine(
            repository.activeConversation,
            repository.messages,
            repository.conversations,
            repository.memories,
            repository.isMemoryEnabled
        ) { activeConv, msgs, convs, mems, memEnabled ->
            CombinedRepoState(activeConv, msgs, convs, mems, memEnabled)
        },
        combine(
            repository.isGenerating,
            repository.searchPhase,
            _inputText,
            _selectedModel,
            _searchMode
        ) { isGen, sPhase, input, model, sMode ->
            CombinedGenState(isGen, sPhase, input, model, sMode)
        },
        combine(
            combine(
                _voiceState,
                _voiceSettings,
                _isDarkTheme,
                _isSettingsOpen,
                _isHistoryOpen
            ) { vState, vSettings, dark, settingsOpen, histOpen ->
                CombinedUiControlState(vState, vSettings, dark, settingsOpen, histOpen)
            },
            combine(
                _isCheckingUpdate,
                _updateInfo,
                _updateStatusMessage,
                _updateSourceUrl,
                _isUpdateSourceConfigOpen
            ) { isChecking, info, status, url, configOpen ->
                CombinedUpdateState(isChecking, info, status, url, configOpen)
            }
        ) { uiCtrl, updateState ->
            Pair(uiCtrl, updateState)
        },
        combine(
            _isAttachmentPickerOpen,
            _pendingAttachments,
            _isProcessingAttachment,
            _snackbarMessage,
            _bannerError
        ) { attOpen, pendingAtts, processing, snack, banner ->
            Pair(Triple(attOpen, pendingAtts, processing), Pair(snack, banner))
        },
        combine(
            _historySearchQuery,
            _historyDateFilter,
            _historyCustomDateEpoch,
            _showArchivedInHistory,
            _renameConversationDialogState
        ) { query, dateFilter, customEpoch, showArchived, renameConv ->
            CombinedMemoryUiState(
                showArchived = showArchived,
                renameConversation = renameConv,
                deleteConversation = _deleteConversationDialogState.value,
                isManageMemoryOpen = _isManageMemoryOpen.value,
                clearMemoriesConfirmationOpen = _clearMemoriesConfirmationOpen.value,
                historySearchQuery = query,
                historyDateFilter = dateFilter,
                historyCustomDateEpoch = customEpoch
            )
        }
    ) { repo, gen, uiAndUpd, dialogPair, memUi ->
        val uiCtrl = uiAndUpd.first
        val updateState = uiAndUpd.second
        val attOpen = dialogPair.first.first
        val pendingAtts = dialogPair.first.second
        val isProcessingAtt = dialogPair.first.third
        val snackbarMsg = dialogPair.second.first
        val bannerErr = dialogPair.second.second

        // Compute filtered conversations for HistoryDrawer based on search query, date filter, and archived tab
        val query = memUi.historySearchQuery.trim()
        val allConvs = repo.conversations
        val filtered = allConvs.filter { conv ->
            val matchesArchived = if (memUi.showArchived) conv.isArchived else !conv.isArchived
            val matchesDate = com.example.util.DateFilterHelper.matchesDate(
                timestamp = conv.updatedAt,
                filter = memUi.historyDateFilter,
                customDateEpoch = memUi.historyCustomDateEpoch
            )
            val matchesQuery = com.example.util.DateFilterHelper.matchesQueryOrDate(conv, query)
            matchesArchived && matchesDate && matchesQuery
        }

        ChatUiState(
            conversation = repo.activeConversation,
            messages = repo.messages,
            allConversations = allConvs,
            filteredConversations = filtered,
            historySearchQuery = memUi.historySearchQuery,
            historyDateFilter = memUi.historyDateFilter,
            historyCustomDateEpoch = memUi.historyCustomDateEpoch,
            showArchivedInHistory = memUi.showArchived,
            renameConversationDialogState = memUi.renameConversation,
            deleteConversationDialogState = memUi.deleteConversation,
            memories = repo.memories,
            isMemoryEnabled = repo.isMemoryEnabled,
            isManageMemoryOpen = memUi.isManageMemoryOpen,
            clearMemoriesConfirmationOpen = _clearMemoriesConfirmationOpen.value,
            isGenerating = gen.isGenerating,
            searchPhase = gen.searchPhase,
            inputText = gen.inputText,
            selectedModel = gen.selectedModel.copy(searchMode = gen.searchMode),
            searchMode = gen.searchMode,
            voiceState = uiCtrl.voiceState,
            voiceSettings = uiCtrl.voiceSettings,
            isDarkTheme = uiCtrl.isDarkTheme,
            isSettingsOpen = uiCtrl.isSettingsOpen,
            isHistoryOpen = uiCtrl.isHistoryOpen,
            isAttachmentPickerOpen = attOpen,
            pendingAttachments = pendingAtts,
            isProcessingAttachment = isProcessingAtt,
            snackbarMessage = snackbarMsg,
            bannerError = bannerErr,
            isCheckingUpdate = updateState.isChecking,
            updateInfo = updateState.info,
            updateStatusMessage = updateState.status,
            updateSourceUrl = updateState.url,
            isUpdateSourceConfigOpen = updateState.configOpen
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ChatUiState()
    )

    fun onEvent(event: ChatUiEvent) {
        when (event) {
            is ChatUiEvent.InputTextChanged -> {
                _inputText.value = event.newText
            }

            ChatUiEvent.SendClicked -> {
                textToSpeechService?.stop()
                speechRecognizerService?.stopListening()

                if (repository.isGenerating.value) {
                    // Prevent duplicate concurrent requests
                    return
                }

                val currentText = _inputText.value.trim()
                val attachments = _pendingAttachments.value
                if (currentText.isEmpty() && attachments.isEmpty()) {
                    _snackbarMessage.value = "Please enter a message or attach a file."
                    return
                }
                _inputText.value = ""
                _pendingAttachments.value = emptyList()
                _bannerError.value = null
                viewModelScope.launch {
                    repository.sendMessage(currentText, _selectedModel.value, attachments)
                }
            }

            is ChatUiEvent.SuggestionClicked -> {
                textToSpeechService?.stop()
                if (repository.isGenerating.value) return
                _inputText.value = ""
                _bannerError.value = null
                viewModelScope.launch {
                    repository.sendMessage(event.prompt, _selectedModel.value, emptyList())
                }
            }

            ChatUiEvent.RetryLastFailed -> {
                textToSpeechService?.stop()
                if (repository.isGenerating.value) return
                _bannerError.value = null
                viewModelScope.launch {
                    repository.retryLastFailed(_selectedModel.value)
                }
            }

            ChatUiEvent.NewChatClicked -> {
                textToSpeechService?.stop()
                speechRecognizerService?.cancelListening()
                _pendingAttachments.value = emptyList()
                _inputText.value = ""
                _bannerError.value = null
                viewModelScope.launch {
                    repository.startNewConversation()
                    _isHistoryOpen.value = false
                }
            }

            is ChatUiEvent.SelectConversation -> {
                textToSpeechService?.stop()
                speechRecognizerService?.cancelListening()
                _pendingAttachments.value = emptyList()
                _inputText.value = ""
                _bannerError.value = null
                viewModelScope.launch {
                    repository.selectConversation(event.conversationId)
                    _isHistoryOpen.value = false
                }
            }

            is ChatUiEvent.DeleteConversation -> {
                viewModelScope.launch {
                    repository.deleteConversation(event.conversationId)
                    _snackbarMessage.value = "Conversation deleted."
                }
            }

            is ChatUiEvent.RenameConversation -> {
                viewModelScope.launch {
                    repository.renameConversation(event.conversationId, event.newTitle)
                    _renameConversationDialogState.value = null
                    _snackbarMessage.value = "Conversation renamed."
                }
            }

            is ChatUiEvent.TogglePinConversation -> {
                viewModelScope.launch {
                    repository.togglePinConversation(event.conversationId)
                }
            }

            is ChatUiEvent.ToggleArchiveConversation -> {
                viewModelScope.launch {
                    repository.toggleArchiveConversation(event.conversationId)
                }
            }

            is ChatUiEvent.RequestRenameConversation -> {
                _renameConversationDialogState.value = event.conversation
            }

            is ChatUiEvent.RequestDeleteConversation -> {
                _deleteConversationDialogState.value = event.conversation
            }

            ChatUiEvent.ConfirmDeleteConversation -> {
                val target = _deleteConversationDialogState.value
                _deleteConversationDialogState.value = null
                if (target != null) {
                    viewModelScope.launch {
                        repository.deleteConversation(target.id)
                        _snackbarMessage.value = "Conversation deleted."
                    }
                }
            }

            ChatUiEvent.DismissDeleteConversation -> {
                _deleteConversationDialogState.value = null
            }

            is ChatUiEvent.HistorySearchQueryChanged -> {
                _historySearchQuery.value = event.query
            }

            is ChatUiEvent.HistoryDateFilterChanged -> {
                _historyDateFilter.value = event.filter
                _historyCustomDateEpoch.value = event.customDateEpoch
            }

            ChatUiEvent.ClearHistoryFilters -> {
                _historySearchQuery.value = ""
                _historyDateFilter.value = com.example.util.HistoryDateFilter.ALL
                _historyCustomDateEpoch.value = null
            }

            is ChatUiEvent.ToggleHistoryArchivedFilter -> {
                _showArchivedInHistory.value = event.showArchived
            }

            // Memory Events
            is ChatUiEvent.ToggleMemoryEnabled -> {
                repository.setMemoryEnabled(event.enabled)
                _snackbarMessage.value = if (event.enabled) "Mayra AI memory enabled." else "Mayra AI memory paused."
            }

            ChatUiEvent.OpenManageMemory -> _isManageMemoryOpen.value = true
            ChatUiEvent.CloseManageMemory -> _isManageMemoryOpen.value = false

            is ChatUiEvent.AddMemory -> {
                viewModelScope.launch {
                    repository.saveMemory(event.content, event.category)
                    _snackbarMessage.value = "Saved to memory."
                }
            }

            is ChatUiEvent.ToggleMemoryItem -> {
                viewModelScope.launch {
                    repository.toggleMemoryItemEnabled(event.memoryId, event.enabled)
                }
            }

            is ChatUiEvent.DeleteMemoryItem -> {
                viewModelScope.launch {
                    repository.deleteMemory(event.memoryId)
                    _snackbarMessage.value = "Memory item deleted."
                }
            }

            ChatUiEvent.RequestClearMemories -> _clearMemoriesConfirmationOpen.value = true

            ChatUiEvent.ConfirmClearMemories -> {
                _clearMemoriesConfirmationOpen.value = false
                viewModelScope.launch {
                    repository.clearAllMemories()
                    _snackbarMessage.value = "All memories cleared."
                }
            }

            ChatUiEvent.DismissClearMemories -> _clearMemoriesConfirmationOpen.value = false

            is ChatUiEvent.ModelSelected -> {
                _selectedModel.value = event.model
                _snackbarMessage.value = "Switched to ${event.model.displayName}"
            }

            is ChatUiEvent.TemperatureChanged -> {
                _selectedModel.value = _selectedModel.value.copy(temperature = event.temperature)
            }

            is ChatUiEvent.SearchModeChanged -> {
                _searchMode.value = event.mode
                _snackbarMessage.value = "Web search set to ${event.mode.displayName}"
            }

            ChatUiEvent.OpenSettings -> _isSettingsOpen.value = true
            ChatUiEvent.CloseSettings -> {
                _isSettingsOpen.value = false
                _isManageMemoryOpen.value = false
            }

            ChatUiEvent.OpenHistory -> _isHistoryOpen.value = true
            ChatUiEvent.CloseHistory -> {
                _isHistoryOpen.value = false
                _historySearchQuery.value = ""
                _historyDateFilter.value = com.example.util.HistoryDateFilter.ALL
                _historyCustomDateEpoch.value = null
            }

            ChatUiEvent.ToggleTheme -> _isDarkTheme.value = !_isDarkTheme.value

            ChatUiEvent.AttachmentPlaceholderClicked,
            ChatUiEvent.OpenAttachmentPicker -> {
                _isAttachmentPickerOpen.value = true
            }

            ChatUiEvent.CloseAttachmentPicker -> {
                _isAttachmentPickerOpen.value = false
            }

            is ChatUiEvent.SetAttachmentProcessing -> {
                _isProcessingAttachment.value = event.isProcessing
            }

            is ChatUiEvent.AttachmentsSelected -> {
                _isProcessingAttachment.value = false
                _isAttachmentPickerOpen.value = false
                val current = _pendingAttachments.value.toMutableList()
                for (newAtt in event.attachments) {
                    if (current.none { it.id == newAtt.id }) {
                        current.add(newAtt)
                    }
                }
                _pendingAttachments.value = current
            }

            is ChatUiEvent.RemovePendingAttachment -> {
                _pendingAttachments.value = _pendingAttachments.value.filter { it.id != event.attachmentId }
            }

            is ChatUiEvent.AttachmentError -> {
                _isProcessingAttachment.value = false
                _bannerError.value = event.errorMessage
                _isAttachmentPickerOpen.value = false
            }

            ChatUiEvent.VoicePlaceholderClicked -> {
                if (_voiceState.value is VoiceState.Listening) {
                    onEvent(ChatUiEvent.StopVoiceInput)
                } else {
                    onEvent(ChatUiEvent.StartVoiceInput)
                }
            }

            ChatUiEvent.StartVoiceInput -> {
                textToSpeechService?.stop()
                val targetLang = _voiceSettings.value.inputLanguage
                speechRecognizerService?.startListening(targetLang) { recognized ->
                    val trimmedRecognized = recognized.trim()
                    if (trimmedRecognized.isNotEmpty()) {
                        val current = _inputText.value.trim()
                        _inputText.value = if (current.isEmpty()) trimmedRecognized else "$current $trimmedRecognized"
                        _snackbarMessage.value = "Speech converted to text. You can edit before sending."
                    }
                }
            }

            ChatUiEvent.StopVoiceInput -> {
                speechRecognizerService?.stopListening()
            }

            ChatUiEvent.CancelVoiceInput -> {
                speechRecognizerService?.cancelListening()
            }

            ChatUiEvent.VoicePermissionDenied -> {
                _voiceState.value = VoiceState.Error("Microphone permission was denied. Text chat remains functional.")
                _snackbarMessage.value = "Microphone permission is required to use voice input."
            }

            is ChatUiEvent.SpeakMessage -> {
                speechRecognizerService?.cancelListening()
                textToSpeechService?.speak(
                    messageId = event.messageId,
                    text = event.text,
                    language = _voiceSettings.value.outputLanguage
                )
            }

            ChatUiEvent.PauseSpeech -> textToSpeechService?.pause()
            ChatUiEvent.ResumeSpeech -> textToSpeechService?.resume()
            ChatUiEvent.StopSpeech -> textToSpeechService?.stop()

            is ChatUiEvent.VoiceInputLanguageChanged -> {
                voicePreferences?.updateInputLanguage(event.language) ?: run {
                    _voiceSettings.value = _voiceSettings.value.copy(inputLanguage = event.language)
                }
                _snackbarMessage.value = "Voice input language: ${event.language.displayName}"
            }

            is ChatUiEvent.VoiceAutoSpeakToggled -> {
                voicePreferences?.updateAutoSpeak(event.enabled) ?: run {
                    _voiceSettings.value = _voiceSettings.value.copy(autoSpeakOutput = event.enabled)
                }
                _snackbarMessage.value = if (event.enabled) "Auto voice responses enabled" else "Auto voice responses disabled"
            }

            is ChatUiEvent.VoiceOutputLanguageChanged -> {
                voicePreferences?.updateOutputLanguage(event.language) ?: run {
                    _voiceSettings.value = _voiceSettings.value.copy(outputLanguage = event.language)
                }
                _snackbarMessage.value = "Voice output language: ${event.language.displayName}"
            }

            ChatUiEvent.ClearCurrentChat -> {
                textToSpeechService?.stop()
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

            // App Update Events
            ChatUiEvent.CheckForAppUpdate -> {
                viewModelScope.launch {
                    _isCheckingUpdate.value = true
                    _updateStatusMessage.value = "Checking for Mayra AI updates..."
                    val url = appUpdatePreferences?.getUpdateUrl() ?: com.example.data.local.AppUpdatePreferences.DEFAULT_UPDATE_URL
                    val result = appUpdateService.checkForUpdate(url)
                    _isCheckingUpdate.value = false
                    if (result.isSuccess) {
                        val info = result.getOrNull()
                        _updateInfo.value = info
                        if (info?.isUpdateAvailable == true) {
                            _updateStatusMessage.value = "New version ${info.latestVersion} available!"
                        } else {
                            _updateStatusMessage.value = "Mayra AI is up to date (v${info?.currentVersion ?: "1.0"})."
                        }
                    } else {
                        _updateStatusMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to check for updates."
                    }
                }
            }

            is ChatUiEvent.UpdateSourceUrlChanged -> {
                appUpdatePreferences?.setUpdateUrl(event.url)
                _updateSourceUrl.value = event.url
                _snackbarMessage.value = "Update source URL updated."
            }

            ChatUiEvent.ResetUpdateSourceUrl -> {
                appUpdatePreferences?.resetToDefault()
                _updateSourceUrl.value = com.example.data.local.AppUpdatePreferences.DEFAULT_UPDATE_URL
                _snackbarMessage.value = "Update source reset to default."
            }

            ChatUiEvent.ToggleUpdateSourceConfig -> {
                _isUpdateSourceConfigOpen.value = !_isUpdateSourceConfigOpen.value
            }

            is ChatUiEvent.DownloadAppUpdate -> {
                if (event.url.isNotBlank()) {
                    appUpdateService.openDownloadUrl(event.context, event.url)
                }
            }

            ChatUiEvent.DismissUpdateDialog -> {
                _updateInfo.value = null
                _updateStatusMessage.value = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizerService?.destroy()
        textToSpeechService?.destroy()
    }

    companion object {
        fun provideFactory(
            repository: ChatRepository,
            speechRecognizerService: SpeechRecognizerService? = null,
            textToSpeechService: TextToSpeechService? = null,
            voicePreferences: VoicePreferences? = null,
            appUpdatePreferences: com.example.data.local.AppUpdatePreferences? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(
                        repository = repository,
                        speechRecognizerService = speechRecognizerService,
                        textToSpeechService = textToSpeechService,
                        voicePreferences = voicePreferences,
                        appUpdatePreferences = appUpdatePreferences
                    ) as T
                }
            }
    }

    // Helper data classes for combine decomposition
    private data class CombinedUpdateState(
        val isChecking: Boolean,
        val info: com.example.data.service.AppUpdateInfo?,
        val status: String?,
        val url: String,
        val configOpen: Boolean
    )

    private data class CombinedRepoState(
        val activeConversation: Conversation?,
        val messages: List<ChatMessage>,
        val conversations: List<Conversation>,
        val memories: List<MemoryItem>,
        val isMemoryEnabled: Boolean
    )

    private data class CombinedGenState(
        val isGenerating: Boolean,
        val searchPhase: SearchPhase,
        val inputText: String,
        val selectedModel: AiModelConfig,
        val searchMode: SearchMode
    )

    private data class CombinedUiControlState(
        val voiceState: VoiceState,
        val voiceSettings: VoiceSettings,
        val isDarkTheme: Boolean,
        val isSettingsOpen: Boolean,
        val isHistoryOpen: Boolean
    )

    private data class CombinedDialogState(
        val isAttachmentPickerOpen: Boolean,
        val pendingAttachments: List<com.example.domain.model.Attachment>,
        val snackbarMessage: String?,
        val bannerError: String?,
        val historySearchQuery: String
    )

    private data class CombinedMemoryUiState(
        val showArchived: Boolean,
        val renameConversation: Conversation?,
        val deleteConversation: Conversation?,
        val isManageMemoryOpen: Boolean,
        val clearMemoriesConfirmationOpen: Boolean,
        val historySearchQuery: String,
        val historyDateFilter: com.example.util.HistoryDateFilter = com.example.util.HistoryDateFilter.ALL,
        val historyCustomDateEpoch: Long? = null
    )
}
