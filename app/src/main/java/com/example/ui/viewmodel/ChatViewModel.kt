package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ChatRepository
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.repository.StreamEvent
import com.example.speech.SpeechRecognizerHelper
import com.example.speech.TextToSpeechHelper
import com.example.ui.components.AttachmentInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ChatRepository = ChatRepositoryImpl(application)
) : AndroidViewModel(application) {

    val speechRecognizerHelper = SpeechRecognizerHelper(application)
    val textToSpeechHelper = TextToSpeechHelper(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeStreamJob: Job? = null
    private var messagesJob: Job? = null

    init {
        observeConversations()
    }

    private fun observeConversations() {
        repository.getConversations()
            .onEach { convs ->
                _uiState.update { current ->
                    val selectedId = current.currentConversationId ?: convs.firstOrNull()?.id
                    current.copy(
                        conversations = convs,
                        currentConversationId = selectedId
                    )
                }

                val currentId = _uiState.value.currentConversationId
                if (currentId != null) {
                    observeMessages(currentId)
                } else if (convs.isEmpty()) {
                    // Create default conversation if none exist
                    viewModelScope.launch {
                        val newConv = repository.createConversation("New Chat")
                        selectConversation(newConv.id)
                    }
                }
            }
            .launchIn(viewModelScope)

        repository.getArchivedConversations()
            .onEach { archived ->
                _uiState.update { it.copy(archivedConversations = archived) }
            }
            .launchIn(viewModelScope)
    }

    fun selectConversation(id: String) {
        _uiState.update { it.copy(currentConversationId = id) }
        observeMessages(id)
        viewModelScope.launch {
            val conv = repository.getConversationById(id)
            if (conv != null) {
                _uiState.update { it.copy(currentConversationTitle = conv.title) }
            }
        }
    }

    private fun observeMessages(conversationId: String) {
        messagesJob?.cancel()
        messagesJob = repository.getMessagesForConversation(conversationId)
            .onEach { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
            .launchIn(viewModelScope)
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onAttachmentSelected(attachment: AttachmentInfo?) {
        _uiState.update { it.copy(attachedFile = attachment) }
    }

    fun newChat() {
        viewModelScope.launch {
            val newConv = repository.createConversation("New Chat")
            selectConversation(newConv.id)
        }
    }

    fun sendMessage() {
        val currentState = _uiState.value
        val prompt = currentState.inputText.trim()
        val attachment = currentState.attachedFile
        val convId = currentState.currentConversationId ?: return

        if (prompt.isBlank() && attachment == null) return
        if (currentState.isGenerating) return

        // Clear input and show generating/thinking state
        _uiState.update {
            it.copy(
                inputText = "",
                attachedFile = null,
                isGenerating = true,
                isThinking = true,
                errorMessage = null
            )
        }

        activeStreamJob?.cancel()
        activeStreamJob = viewModelScope.launch {
            try {
                repository.sendMessage(
                    conversationId = convId,
                    userPrompt = prompt,
                    attachmentBytes = attachment?.bytes,
                    attachmentMimeType = attachment?.mimeType,
                    attachmentName = attachment?.name,
                    attachmentUriString = attachment?.uri?.toString()
                ).catch { e ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            isThinking = false,
                            errorMessage = e.message ?: "Failed to generate response"
                        )
                    }
                }.collect { event ->
                    when (event) {
                        is StreamEvent.TextChunk -> {
                            // Immediately hide thinking indicator as soon as first chunk arrives!
                            _uiState.update { it.copy(isThinking = false) }
                        }
                        is StreamEvent.SourcesDiscovered -> {
                            _uiState.update { it.copy(isThinking = false) }
                        }
                        is StreamEvent.Completed -> {
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    isThinking = false
                                )
                            }
                        }
                        is StreamEvent.Error -> {
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    isThinking = false,
                                    errorMessage = event.message
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        isThinking = false,
                        errorMessage = e.message
                    )
                }
            } finally {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        isThinking = false
                    )
                }
            }
        }
    }

    fun retryMessage(messageId: String) {
        val convId = _uiState.value.currentConversationId ?: return
        if (_uiState.value.isGenerating) return

        _uiState.update {
            it.copy(
                isGenerating = true,
                isThinking = true,
                errorMessage = null
            )
        }

        activeStreamJob?.cancel()
        activeStreamJob = viewModelScope.launch {
            try {
                repository.retryMessage(convId, messageId)
                    .catch { e ->
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                isThinking = false,
                                errorMessage = e.message
                            )
                        }
                    }
                    .collect { event ->
                        when (event) {
                            is StreamEvent.TextChunk -> {
                                _uiState.update { it.copy(isThinking = false) }
                            }
                            is StreamEvent.SourcesDiscovered -> {
                                _uiState.update { it.copy(isThinking = false) }
                            }
                            is StreamEvent.Completed -> {
                                _uiState.update {
                                    it.copy(
                                        isGenerating = false,
                                        isThinking = false
                                    )
                                }
                            }
                            is StreamEvent.Error -> {
                                _uiState.update {
                                    it.copy(
                                        isGenerating = false,
                                        isThinking = false,
                                        errorMessage = event.message
                                    )
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        isThinking = false,
                        errorMessage = e.message
                    )
                }
            } finally {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        isThinking = false
                    )
                }
            }
        }
    }

    fun stopGeneration() {
        activeStreamJob?.cancel()
        activeStreamJob = null
        _uiState.update {
            it.copy(
                isGenerating = false,
                isThinking = false
            )
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            repository.updateConversationTitle(id, newTitle)
            if (_uiState.value.currentConversationId == id) {
                _uiState.update { it.copy(currentConversationTitle = newTitle) }
            }
        }
    }

    fun togglePinConversation(id: String, isPinned: Boolean) {
        viewModelScope.launch {
            repository.togglePinConversation(id, isPinned)
        }
    }

    fun toggleArchiveConversation(id: String, isArchived: Boolean) {
        viewModelScope.launch {
            repository.toggleArchiveConversation(id, isArchived)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_uiState.value.currentConversationId == id) {
                val remaining = _uiState.value.conversations.filter { it.id != id }
                if (remaining.isNotEmpty()) {
                    selectConversation(remaining.first().id)
                } else {
                    newChat()
                }
            }
        }
    }

    fun speakText(text: String) {
        textToSpeechHelper.speak(text)
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizerHelper.stopListening()
        textToSpeechHelper.shutdown()
        activeStreamJob?.cancel()
        messagesJob?.cancel()
    }
}
