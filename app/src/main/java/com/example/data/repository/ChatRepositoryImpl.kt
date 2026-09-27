package com.example.data.repository

import com.example.data.local.MemoryPreferences
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.MemoryDao
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MemoryItemEntity
import com.example.data.service.DefaultSearchIntentDetector
import com.example.domain.model.AiModelConfig
import com.example.domain.model.ChatMessage
import com.example.domain.model.Conversation
import com.example.domain.model.MemoryCategory
import com.example.domain.model.MemoryItem
import com.example.domain.model.MessageRole
import com.example.domain.model.MessageStatus
import com.example.domain.model.SearchPhase
import com.example.domain.model.SearchSource
import com.example.domain.service.AiService
import com.example.domain.service.MemoryDetector
import com.example.domain.service.SearchIntentDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class ChatRepositoryImpl(
    private val aiService: AiService,
    private val conversationDao: ConversationDao? = null,
    private val memoryDao: MemoryDao? = null,
    private val memoryPreferences: MemoryPreferences? = null,
    private val searchIntentDetector: SearchIntentDetector = DefaultSearchIntentDetector(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : ChatRepository {

    private val _activeConversation = MutableStateFlow<Conversation?>(null)
    override val activeConversation: StateFlow<Conversation?> = _activeConversation.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    override val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _memories = MutableStateFlow<List<MemoryItem>>(emptyList())
    override val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow()

    private val _isMemoryEnabled = MutableStateFlow(memoryPreferences?.isMemoryEnabled?.value ?: true)
    override val isMemoryEnabled: StateFlow<Boolean> = _isMemoryEnabled.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    override val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _searchPhase = MutableStateFlow(SearchPhase.IDLE)
    override val searchPhase: StateFlow<SearchPhase> = _searchPhase.asStateFlow()

    private var activeGenerationJob: Job? = null
    private var messagesCollectionJob: Job? = null

    // Fallback in-memory storage
    private val inMemoryMessages = mutableListOf<ChatMessage>()

    init {
        // Collect conversations from Room DAO
        if (conversationDao != null) {
            scope.launch {
                conversationDao.getAllConversations().collect { entities ->
                    val domainList = entities.map { it.toDomain() }
                    if (domainList.isNotEmpty()) {
                        _conversations.value = domainList
                        if (_activeConversation.value == null) {
                            selectConversation(domainList.first().id)
                        }
                    }
                }
            }
        }

        // Collect memories from MemoryDao
        if (memoryDao != null) {
            scope.launch {
                memoryDao.getAllMemories().collect { entities ->
                    val domainList = entities.map { it.toDomain() }
                    if (domainList.isNotEmpty() || _memories.value.isEmpty()) {
                        _memories.value = domainList
                    }
                }
            }
        }

        // Collect memory preferences
        if (memoryPreferences != null) {
            scope.launch {
                memoryPreferences.isMemoryEnabled.collect { enabled ->
                    _isMemoryEnabled.value = enabled
                }
            }
        }

        // Ensure at least one initial conversation
        val initial = Conversation(
            id = UUID.randomUUID().toString(),
            title = "New Chat",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        if (_activeConversation.value == null) {
            _activeConversation.value = initial
            _conversations.value = listOf(initial)
        }
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

        val updatedList = (listOf(newConv) + _conversations.value.filter { it.id != newConv.id })
            .sortedWith(compareByDescending<Conversation> { it.isPinned }.thenByDescending { it.updatedAt })
        _conversations.value = updatedList

        conversationDao?.insertConversation(ConversationEntity.fromDomain(newConv))
        selectConversation(newConv.id)
        return newConv
    }

    override suspend fun selectConversation(conversationId: String) {
        cancelGeneration()
        messagesCollectionJob?.cancel()

        val conv = _conversations.value.find { it.id == conversationId } ?: return
        _activeConversation.value = conv

        if (conversationDao != null) {
            messagesCollectionJob = scope.launch {
                conversationDao.getMessagesForConversation(conversationId).collect { entities ->
                    if (entities.isNotEmpty()) {
                        _messages.value = entities.map { it.toDomain() }
                    }
                }
            }
        }
        val currentLocal = inMemoryMessages.filter { it.conversationId == conversationId }
        if (currentLocal.isNotEmpty() || _messages.value.any { it.conversationId != conversationId }) {
            _messages.value = currentLocal
        }
    }

    override suspend fun renameConversation(conversationId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()

        _conversations.value = _conversations.value.map {
            if (it.id == conversationId) it.copy(title = trimmed, updatedAt = now) else it
        }

        if (_activeConversation.value?.id == conversationId) {
            _activeConversation.value = _activeConversation.value?.copy(title = trimmed, updatedAt = now)
        }

        conversationDao?.updateConversationTitle(conversationId, trimmed, now)
    }

    override suspend fun togglePinConversation(conversationId: String) {
        val target = _conversations.value.find { it.id == conversationId } ?: return
        val newPinned = !target.isPinned

        val updated = _conversations.value.map {
            if (it.id == conversationId) it.copy(isPinned = newPinned) else it
        }.sortedWith(compareByDescending<Conversation> { it.isPinned }.thenByDescending { it.updatedAt })

        _conversations.value = updated
        if (_activeConversation.value?.id == conversationId) {
            _activeConversation.value = _activeConversation.value?.copy(isPinned = newPinned)
        }

        conversationDao?.updateConversationPinned(conversationId, newPinned)
    }

    override suspend fun toggleArchiveConversation(conversationId: String) {
        val target = _conversations.value.find { it.id == conversationId } ?: return
        val newArchived = !target.isArchived

        val updated = _conversations.value.map {
            if (it.id == conversationId) it.copy(isArchived = newArchived) else it
        }
        _conversations.value = updated
        if (_activeConversation.value?.id == conversationId) {
            _activeConversation.value = _activeConversation.value?.copy(isArchived = newArchived)
        }

        conversationDao?.updateConversationArchived(conversationId, newArchived)
    }

    override suspend fun deleteConversation(conversationId: String) {
        cancelGeneration()
        val remaining = _conversations.value.filter { it.id != conversationId }
        _conversations.value = remaining
        inMemoryMessages.removeAll { it.conversationId == conversationId }

        conversationDao?.deleteConversation(conversationId)

        if (_activeConversation.value?.id == conversationId) {
            if (remaining.isNotEmpty()) {
                selectConversation(remaining.first().id)
            } else {
                startNewConversation()
            }
        }
    }

    override suspend fun clearMessages() {
        cancelGeneration()
        val conv = _activeConversation.value ?: return
        _messages.value = emptyList()
        inMemoryMessages.removeAll { it.conversationId == conv.id }
        conversationDao?.clearMessages(conv.id)
    }

    override suspend fun cancelGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _isGenerating.value = false
        _searchPhase.value = SearchPhase.IDLE
    }

    // Memory Management
    override fun setMemoryEnabled(enabled: Boolean) {
        memoryPreferences?.setMemoryEnabled(enabled) ?: run {
            _isMemoryEnabled.value = enabled
        }
    }

    override suspend fun saveMemory(content: String, category: MemoryCategory): MemoryItem {
        val trimmed = content.trim()
        val now = System.currentTimeMillis()
        val item = MemoryItem(
            id = UUID.randomUUID().toString(),
            content = trimmed,
            category = category,
            createdAt = now,
            updatedAt = now,
            enabled = true
        )
        _memories.value = listOf(item) + _memories.value.filter { it.id != item.id }
        memoryDao?.insertMemory(MemoryItemEntity.fromDomain(item))
        return item
    }

    override suspend fun toggleMemoryItemEnabled(memoryId: String, enabled: Boolean) {
        val now = System.currentTimeMillis()
        _memories.value = _memories.value.map {
            if (it.id == memoryId) it.copy(enabled = enabled, updatedAt = now) else it
        }
        memoryDao?.updateEnabled(memoryId, enabled, now)
    }

    override suspend fun deleteMemory(memoryId: String) {
        _memories.value = _memories.value.filter { it.id != memoryId }
        memoryDao?.deleteMemoryById(memoryId)
    }

    override suspend fun clearAllMemories() {
        _memories.value = emptyList()
        memoryDao?.clearAllMemories()
    }

    // Search
    override suspend fun searchConversations(query: String): List<Conversation> {
        val q = query.trim()
        if (q.isEmpty()) return _conversations.value

        val allConversations = _conversations.value
        val titleMatches = allConversations.filter { it.title.contains(q, ignoreCase = true) }

        val messageMatchingConvIds = mutableSetOf<String>()
        if (conversationDao != null) {
            try {
                val allMsgs = conversationDao.getAllMessages()
                allMsgs.filter { it.content.contains(q, ignoreCase = true) }
                    .forEach { messageMatchingConvIds.add(it.conversationId) }
            } catch (e: Exception) {
                // ignore
            }
        }
        inMemoryMessages.filter { it.content.contains(q, ignoreCase = true) }
            .forEach { messageMatchingConvIds.add(it.conversationId) }

        return (titleMatches + allConversations.filter { it.id in messageMatchingConvIds })
            .distinctBy { it.id }
            .sortedWith(compareByDescending<Conversation> { it.isPinned }.thenByDescending { it.updatedAt })
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

        cancelGeneration()

        var conv = _activeConversation.value
        if (conv == null) {
            conv = startNewConversation()
        }

        // Auto-title conversation on first message locally
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
                .sortedWith(compareByDescending<Conversation> { it.isPinned }.thenByDescending { it.updatedAt })
            conversationDao?.insertConversation(ConversationEntity.fromDomain(updatedConv))
        }

        // Memory auto-learning: ONLY when memory is enabled and user explicitly requests to remember
        if (_isMemoryEnabled.value && trimmed.isNotEmpty()) {
            val extracted = MemoryDetector.extractMemory(trimmed)
            if (extracted != null) {
                saveMemory(extracted.content, extracted.category)
            }
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

        inMemoryMessages.add(userMessage)
        _messages.value = _messages.value + userMessage
        persistMessage(userMessage, conv)

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

        val userMsg = current.take(lastErrorIndex).lastOrNull { it.role == MessageRole.USER }
            ?: return Result.failure(IllegalStateException("No preceding user prompt found."))

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
    ): Result<ChatMessage> = coroutineScope {
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

        activeGenerationJob = coroutineContext[Job]

        try {
            // Long chat safety: Ensure conversation history is limited for the Gemini request window,
            // while Room database retains all stored history.
            val historySnapshot = _messages.value.filter { it.conversationId == conv.id }
            val boundedHistory = if (historySnapshot.size > 20) {
                historySnapshot.takeLast(20)
            } else {
                historySnapshot
            }

            // Inject relevant enabled memories into system prompt if memory is ON
            val effectiveConfig = if (_isMemoryEnabled.value) {
                val enabledMemories = _memories.value.filter { it.enabled }
                if (enabledMemories.isNotEmpty()) {
                    val memoryContext = buildMemoryContext(enabledMemories, prompt)
                    val combinedSysPrompt = if (config.systemPrompt.isNotBlank()) {
                        "${config.systemPrompt}\n\n$memoryContext"
                    } else {
                        memoryContext
                    }
                    config.copy(systemPrompt = combinedSysPrompt)
                } else {
                    config
                }
            } else {
                config
            }

            aiService.generateStream(conv.id, prompt, boundedHistory, effectiveConfig, attachments, enableSearch)
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
                        inMemoryMessages.add(finalMsg)
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
                inMemoryMessages.add(partialMsg)
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
            activeGenerationJob = null
        }

        if (failureResult != null) {
            Result.failure(failureResult!!)
        } else {
            val msg = completedMessage ?: _messages.value.find { it.id == assistantMsgId }
            if (msg != null) Result.success(msg) else Result.failure(IllegalStateException("No message created."))
        }
    }

    private fun buildMemoryContext(memories: List<MemoryItem>, prompt: String): String {
        val relevant = selectRelevantMemories(memories, prompt)
        val sb = StringBuilder("[User Profile & Context Memories]:\n")
        for (m in relevant) {
            sb.append("- [${m.category.displayName}]: ${m.content}\n")
        }
        sb.append("Apply these user instructions and preferences when relevant.")
        return sb.toString().trim()
    }

    private fun selectRelevantMemories(
        memories: List<MemoryItem>,
        prompt: String,
        maxCount: Int = 10
    ): List<MemoryItem> {
        if (memories.size <= maxCount) return memories
        val promptWords = prompt.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.length > 2 }
        return memories.sortedByDescending { memory ->
            val memLower = memory.content.lowercase(Locale.ROOT)
            var score = 0
            for (w in promptWords) {
                if (memLower.contains(w)) score += 2
            }
            if (memory.category == MemoryCategory.PREFERENCE || memory.category == MemoryCategory.INSTRUCTION) {
                score += 1
            }
            score
        }.take(maxCount)
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
