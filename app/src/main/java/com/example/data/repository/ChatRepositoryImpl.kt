package com.example.data.repository

import com.example.data.local.dao.ConversationDao
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ConversationEntity
import com.example.domain.model.AiModelConfig
import com.example.domain.model.ChatMessage
import com.example.domain.model.Conversation
import com.example.domain.model.MessageRole
import com.example.domain.model.MessageStatus
import com.example.domain.service.AiService
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
    }

    override suspend fun sendMessage(content: String, config: AiModelConfig): Result<ChatMessage> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message cannot be empty."))
        }

        // Cancel any in-flight stream before starting new turn
        cancelGeneration()

        var conv = _activeConversation.value
        if (conv == null) {
            conv = startNewConversation()
        }

        // Auto-title conversation on first message
        if (conv.title == "New Chat" && _messages.value.isEmpty()) {
            val titleCandidate = if (trimmed.length > 32) trimmed.take(29) + "…" else trimmed
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
            status = MessageStatus.SENT
        )

        // Append user message immediately
        _messages.value = _messages.value + userMessage
        persistMessage(userMessage, conv)

        return executeAiStreaming(conv, trimmed, config)
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
        return executeAiStreaming(conv, userMsg.content, config)
    }

    private suspend fun executeAiStreaming(
        conv: Conversation,
        prompt: String,
        config: AiModelConfig
    ): Result<ChatMessage> {
        _isGenerating.value = true
        val assistantMsgId = UUID.randomUUID().toString()
        var hasAddedAssistantMsg = false
        val textAccumulator = StringBuilder()
        var failureResult: Throwable? = null
        var completedMessage: ChatMessage? = null

        val job = scope.launch {
            try {
                val historySnapshot = _messages.value
                aiService.generateStream(conv.id, prompt, historySnapshot, config)
                    .collect { chunk ->
                        if (!chunk.isComplete) {
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
                                    status = MessageStatus.SENDING
                                )
                                _messages.value = _messages.value + assistantMessage
                            } else {
                                _messages.value = _messages.value.map { msg ->
                                    if (msg.id == assistantMsgId) {
                                        msg.copy(content = currentText)
                                    } else msg
                                }
                            }
                        } else {
                            val finalContent = textAccumulator.toString().trim()
                            val finalMsg = ChatMessage(
                                id = assistantMsgId,
                                conversationId = conv.id,
                                role = MessageRole.ASSISTANT,
                                content = finalContent,
                                timestamp = System.currentTimeMillis(),
                                status = MessageStatus.SENT
                            )
                            completedMessage = finalMsg
                            _messages.value = _messages.value.map { msg ->
                                if (msg.id == assistantMsgId) finalMsg else msg
                            }
                            persistMessage(finalMsg, conv)
                        }
                    }
            } catch (e: CancellationException) {
                if (hasAddedAssistantMsg && textAccumulator.isNotEmpty()) {
                    val partialMsg = ChatMessage(
                        id = assistantMsgId,
                        conversationId = conv.id,
                        role = MessageRole.ASSISTANT,
                        content = textAccumulator.toString().trim(),
                        timestamp = System.currentTimeMillis(),
                        status = MessageStatus.SENT
                    )
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == assistantMsgId) partialMsg else msg
                    }
                    persistMessage(partialMsg, conv)
                }
                throw e
            } catch (e: Exception) {
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
                        errorMessage = errorText
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
