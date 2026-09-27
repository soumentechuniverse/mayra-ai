package com.example.data.repository

import com.example.data.local.dao.ConversationDao
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ConversationEntity
import com.example.data.service.DefaultSearchIntentDetector
import com.example.domain.model.AiModelConfig
import com.example.domain.model.ChatMessage
import com.example.domain.model.Conversation
import com.example.domain.model.MessageRole
import com.example.domain.model.MessageStatus
import com.example.domain.model.SearchPhase
import com.example.domain.model.SearchSource
import com.example.domain.service.AiService
import com.example.domain.service.SearchIntentDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatRepositoryImpl(
    private val aiService: AiService,
    private val conversationDao: ConversationDao? = null,
    private val searchIntentDetector: SearchIntentDetector = DefaultSearchIntentDetector(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : ChatRepository {

    private val _activeConversation = MutableStateFlow<Conversation?>(null)
    override val activeConversation: StateFlow<Conversation?> = _activeConversation.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    override val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    override val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _searchPhase = MutableStateFlow(SearchPhase.IDLE)
    override val searchPhase: StateFlow<SearchPhase> = _searchPhase.asStateFlow()

    private var activeGenerationJob: Job? = null

    init {
        // Observe local database if DAO is available, or initialize default conversation
        if (conversationDao != null) {
            scope.launch {
                conversationDao.getAllConversations().collect { entities ->
                    _conversations.value = entities.map { it.toDomain() }
                }
            }
        }
        val initial = Conversation(
            id = UUID.randomUUID().toString(),
            title = "New Chat",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        _activeConversation.value = initial
        _conversations.value = listOf(initial)
    }

    override suspend fun startNewConversation(title: String): Conversation {
        cancelGeneration()
        val newConv = Conversation(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        _activeConversation.value = newConv
        _messages.value = emptyList()
        _conversations.value = listOf(newConv) + _conversations.value.filter { it.id != newConv.id }
        conversationDao?.insertConversation(ConversationEntity.fromDomain(newConv))
        return newConv
    }

    override suspend fun selectConversation(conversationId: String) {
        cancelGeneration()
        val conv = _conversations.value.find { it.id == conversationId } ?: return
        _activeConversation.value = conv
        if (conversationDao != null) {
            scope.launch {
                conversationDao.getMessagesForConversation(conversationId).collect { entities ->
                    _messages.value = entities.map { it.toDomain() }
                }
            }
        } else {
            _messages.value = _messages.value.filter { it.conversationId == conversationId }
        }
    }

    override suspend fun cancelGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _isGenerating.value = false
        _searchPhase.value = SearchPhase.IDLE
    }

    override suspend fun sendMessage(
        content: String,
        config: AiModelConfig,
        attachments: List<com.example.domain.model.Attachment>
    ): Result<ChatMessage> {
        val trimmed = content.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message or attachment cannot be empty."))
        }

        // Cancel any in-flight stream before starting new turn
        cancelGeneration()

        var conv = _activeConversation.value
        if (conv == null) {
            conv = startNewConversation()
        }

        // Auto-title conversation on first message
        if (conv.title == "New Chat" && _messages.value.isEmpty()) {
            val titleCandidate = when {
                trimmed.isNotEmpty() -> if (trimmed.length > 32) trimmed.take(29) + "…" else trimmed
                attachments.isNotEmpty() -> attachments.first().name
                else -> "New Chat"
            }
            val updatedConv = conv.copy(title = titleCandidate, updatedAt = System.currentTimeMillis())
            conv = updatedConv
            _activeConversation.value = updatedConv
            _conversations.value = _conversations.value.map { if (it.id == conv.id) updatedConv else it }
            conversationDao?.insertConversation(ConversationEntity.fromDomain(updatedConv))
        }

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conv.id,
            role = MessageRole.USER,
            content = trimmed,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT,
            attachments = attachments.map { it.metadata }
        )

        // Append user message immediately
        _messages.value = _messages.value + userMessage
        persistMessage(userMessage, conv)

        // Detect whether current web information is required
        val decision = searchIntentDetector.detect(
            query = trimmed,
            mode = config.searchMode,
            hasAttachments = attachments.isNotEmpty()
        )
        val enableSearch = decision.needsSearch

        return executeAiStreaming(conv, trimmed, config, attachments, enableSearch)
    }

    override suspend fun retryLastFailed(config: AiModelConfig): Result<ChatMessage> {
        cancelGeneration()

        val current = _messages.value
        val lastErrorIndex = current.indexOfLast { it.status == MessageStatus.ERROR }
        if (lastErrorIndex == -1) {
            return Result.failure(IllegalStateException("No failed message found to retry."))
        }

        // Find the last user message before or at this error
        val userMsg = current.take(lastErrorIndex).lastOrNull { it.role == MessageRole.USER }
            ?: return Result.failure(IllegalStateException("No preceding user prompt found."))

        // Remove the error message
        val updated = current.toMutableList()
        updated.removeAt(lastErrorIndex)
        _messages.value = updated

        val conv = _activeConversation.value ?: startNewConversation()
        val decision = searchIntentDetector.detect(
            query = userMsg.content,
            mode = config.searchMode,
            hasAttachments = userMsg.attachments.isNotEmpty()
        )
        return executeAiStreaming(conv, userMsg.content, config, emptyList(), decision.needsSearch)
    }

    private suspend fun executeAiStreaming(
        conv: Conversation,
        prompt: String,
        config: AiModelConfig,
        attachments: List<com.example.domain.model.Attachment> = emptyList(),
        enableSearch: Boolean = false
    ): Result<ChatMessage> {
        _isGenerating.value = true
        if (enableSearch) {
            _searchPhase.value = SearchPhase.SEARCHING
        } else {
            _searchPhase.value = SearchPhase.IDLE
        }
        val assistantMsgId = UUID.randomUUID().toString()
        var hasAddedAssistantMsg = false
        val textAccumulator = StringBuilder()
        val accumulatedSources = mutableListOf<SearchSource>()
        var failureResult: Throwable? = null
        var completedMessage: ChatMessage? = null

        val job = scope.launch {
            try {
                val historySnapshot = _messages.value
                aiService.generateStream(conv.id, prompt, historySnapshot, config, attachments, enableSearch)
                    .collect { chunk ->
                        if (chunk.isSearching) {
                            _searchPhase.value = SearchPhase.SEARCHING
                        } else if (chunk.searchQueries.isNotEmpty() || chunk.searchSources.isNotEmpty()) {
                            _searchPhase.value = SearchPhase.READING_SOURCES
                        }

                        if (chunk.searchSources.isNotEmpty()) {
                            for (s in chunk.searchSources) {
                                if (accumulatedSources.none { it.url == s.url }) {
                                    accumulatedSources.add(s)
                                }
                            }
                        }

                        if (!chunk.isComplete) {
                            if (chunk.textDelta.isNotEmpty()) {
                                _searchPhase.value = SearchPhase.GENERATING
                                textAccumulator.append(chunk.textDelta)
                                val currentText = textAccumulator.toString()
                                if (!hasAddedAssistantMsg) {
                                    hasAddedAssistantMsg = true
                                    val assistantMessage = ChatMessage(
                                        id = assistantMsgId,
                                        conversationId = conv.id,
                                        role = MessageRole.ASSISTANT,
                                        content = currentText,
                                        timestamp = System.currentTimeMillis(),
                                        status = MessageStatus.SENDING,
                                        searchSources = accumulatedSources.toList()
                                    )
                                    _messages.value = _messages.value + assistantMessage
                                } else {
                                    _messages.value = _messages.value.map { msg ->
                                        if (msg.id == assistantMsgId) {
                                            msg.copy(
                                                content = currentText,
                                                searchSources = accumulatedSources.toList()
                                            )
                                        } else msg
                                    }
                                }
                            }
                        } else {
                            _searchPhase.value = SearchPhase.IDLE
                            val finalContent = textAccumulator.toString().trim()
                            val finalMsg = ChatMessage(
                                id = assistantMsgId,
                                conversationId = conv.id,
                                role = MessageRole.ASSISTANT,
                                content = finalContent,
                                timestamp = System.currentTimeMillis(),
                                status = MessageStatus.SENT,
                                searchSources = accumulatedSources.toList()
                            )
                            completedMessage = finalMsg
                            _messages.value = _messages.value.map { msg ->
                                if (msg.id == assistantMsgId) finalMsg else msg
                            }
                            persistMessage(finalMsg, conv)
                        }
                    }
            } catch (e: CancellationException) {
                _searchPhase.value = SearchPhase.IDLE
                if (hasAddedAssistantMsg && textAccumulator.isNotEmpty()) {
                    val partialMsg = ChatMessage(
                        id = assistantMsgId,
                        conversationId = conv.id,
                        role = MessageRole.ASSISTANT,
                        content = textAccumulator.toString().trim(),
                        timestamp = System.currentTimeMillis(),
                        status = MessageStatus.SENT,
                        searchSources = accumulatedSources.toList()
                    )
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == assistantMsgId) partialMsg else msg
                    }
                    persistMessage(partialMsg, conv)
                }
                throw e
            } catch (e: Exception) {
                _searchPhase.value = SearchPhase.IDLE
                failureResult = e
                val errorText = e.localizedMessage ?: "Failed to generate response."
                if (hasAddedAssistantMsg && textAccumulator.isNotEmpty()) {
                    val partialErrorMsg = ChatMessage(
                        id = assistantMsgId,
                        conversationId = conv.id,
                        role = MessageRole.ASSISTANT,
                        content = textAccumulator.toString(),
                        timestamp = System.currentTimeMillis(),
                        status = MessageStatus.ERROR,
                        errorMessage = errorText,
                        searchSources = accumulatedSources.toList()
                    )
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == assistantMsgId) partialErrorMsg else msg
                    }
                } else {
                    val errorMsg = ChatMessage(
                        id = assistantMsgId,
                        conversationId = conv.id,
                        role = MessageRole.ASSISTANT,
                        content = "",
                        timestamp = System.currentTimeMillis(),
                        status = MessageStatus.ERROR,
                        errorMessage = errorText
                    )
                    _messages.value = _messages.value.filter { it.id != assistantMsgId } + errorMsg
                }
            } finally {
                _isGenerating.value = false
                _searchPhase.value = SearchPhase.IDLE
            }
        }

        activeGenerationJob = job
        job.join()

        return if (failureResult != null) {
            Result.failure(failureResult!!)
        } else {
            val msg = completedMessage ?: _messages.value.find { it.id == assistantMsgId }
            if (msg != null) Result.success(msg) else Result.failure(IllegalStateException("No message created."))
        }
    }

    override suspend fun clearMessages() {
        cancelGeneration()
        val conv = _activeConversation.value ?: return
        _messages.value = emptyList()
        conversationDao?.clearMessages(conv.id)
    }

    override suspend fun deleteConversation(conversationId: String) {
        cancelGeneration()
        _conversations.value = _conversations.value.filter { it.id != conversationId }
        conversationDao?.deleteConversation(conversationId)
        if (_activeConversation.value?.id == conversationId) {
            startNewConversation()
        }
    }

    private fun persistMessage(message: ChatMessage, conv: Conversation) {
        if (conversationDao == null) return
        scope.launch {
            val previewText = if (message.content.length > 50) message.content.take(47) + "…" else message.content
            conversationDao.saveMessageAndUpdateConversation(
                ChatMessageEntity.fromDomain(message),
                updatedAt = System.currentTimeMillis(),
                preview = previewText
            )
        }
    }
}
