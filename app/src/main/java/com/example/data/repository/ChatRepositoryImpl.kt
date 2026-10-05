package com.example.data.repository

import com.example.data.local.MemoryPreferences
import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.MemoryDao
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MemoryItemEntity
import com.example.data.service.DefaultSearchIntentDetector
import com.example.data.service.ImageRetrievalService
import com.example.data.service.RetrievedImageResult
import com.example.domain.model.AiModelConfig
import com.example.domain.model.Attachment
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class ChatRepositoryImpl(
    private val aiService: AiService,
    private val conversationDao: ConversationDao? = null,
    private val memoryDao: MemoryDao? = null,
    private val memoryPreferences: MemoryPreferences? = null,
    private val searchIntentDetector: SearchIntentDetector =
        DefaultSearchIntentDetector(),
    private val imageRetrievalService: ImageRetrievalService =
        com.example.data.service.WikimediaImageRetrievalService(),
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : ChatRepository {

    private val _activeConversation =
        MutableStateFlow<Conversation?>(null)

    override val activeConversation: StateFlow<Conversation?> =
        _activeConversation.asStateFlow()

    private val _messages =
        MutableStateFlow<List<ChatMessage>>(emptyList())

    override val messages: StateFlow<List<ChatMessage>> =
        _messages.asStateFlow()

    private val _conversations =
        MutableStateFlow<List<Conversation>>(emptyList())

    override val conversations: StateFlow<List<Conversation>> =
        _conversations.asStateFlow()

    private val _memories =
        MutableStateFlow<List<MemoryItem>>(emptyList())

    override val memories: StateFlow<List<MemoryItem>> =
        _memories.asStateFlow()

    private val _isMemoryEnabled =
        MutableStateFlow(
            memoryPreferences?.isMemoryEnabled?.value ?: true
        )

    override val isMemoryEnabled: StateFlow<Boolean> =
        _isMemoryEnabled.asStateFlow()

    private val _isGenerating =
        MutableStateFlow(false)

    override val isGenerating: StateFlow<Boolean> =
        _isGenerating.asStateFlow()

    private val _searchPhase =
        MutableStateFlow(SearchPhase.IDLE)

    override val searchPhase: StateFlow<SearchPhase> =
        _searchPhase.asStateFlow()

    private var activeGenerationJob: Job? = null
    private var messagesCollectionJob: Job? = null

    private val inMemoryMessages =
        mutableListOf<ChatMessage>()

    private var lastSentAttachments =
        mutableListOf<Attachment>()

    init {

        if (conversationDao != null) {
            scope.launch {
                try {
                    val entities =
                        conversationDao
                            .getAllConversations()
                            .first()

                    val domainList =
                        entities.map { it.toDomain() }

                    if (
                        domainList.isNotEmpty() &&
                        _conversations.value.isEmpty()
                    ) {
                        _conversations.value = domainList

                        if (_activeConversation.value == null) {
                            selectConversation(
                                domainList.first().id
                            )
                        }
                    }

                } catch (_: Exception) {
                    // Safe fallback.
                }
            }
        }

        if (memoryDao != null) {
            scope.launch {
                try {
                    val entities =
                        memoryDao
                            .getAllMemories()
                            .first()

                    val domainList =
                        entities.map { it.toDomain() }

                    if (
                        domainList.isNotEmpty() &&
                        _memories.value.isEmpty()
                    ) {
                        _memories.value = domainList
                    }

                } catch (_: Exception) {
                    // Safe fallback.
                }
            }
        }

        if (memoryPreferences != null) {
            scope.launch {
                memoryPreferences
                    .isMemoryEnabled
                    .collect { enabled ->
                        _isMemoryEnabled.value = enabled
                    }
            }
        }

        if (_activeConversation.value == null) {

            val initial =
                Conversation(
                    id = UUID.randomUUID().toString(),
                    title = "New Chat",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

            _activeConversation.value = initial
            _conversations.value = listOf(initial)

            if (conversationDao != null) {
                scope.launch {
                    try {
                        conversationDao.insertConversation(
                            ConversationEntity.fromDomain(initial)
                        )
                    } catch (_: Exception) {
                        // Safe fallback.
                    }
                }
            }
        }
    }

    override suspend fun startNewConversation(
        title: String
    ): Conversation {

        cancelGeneration()

        val newConversation =
            Conversation(
                id = UUID.randomUUID().toString(),
                title = title,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

        _activeConversation.value = newConversation
        _messages.value = emptyList()

        _conversations.value =
            (
                listOf(newConversation) +
                    _conversations.value.filter {
                        it.id != newConversation.id
                    }
                )
                .sortedWith(
                    compareByDescending<Conversation> {
                        it.isPinned
                    }.thenByDescending {
                        it.updatedAt
                    }
                )

        conversationDao?.insertConversation(
            ConversationEntity.fromDomain(newConversation)
        )

        return newConversation
    }

    override suspend fun selectConversation(
        conversationId: String
    ) {

        cancelGeneration()
        messagesCollectionJob?.cancel()

        val conversation =
            _conversations.value.find {
                it.id == conversationId
            } ?: return

        _activeConversation.value = conversation

        if (conversationDao != null) {

            messagesCollectionJob =
                scope.launch {

                    conversationDao
                        .getMessagesForConversation(
                            conversationId
                        )
                        .collect { entities ->

                            _messages.value =
                                entities.map {
                                    it.toDomain()
                                }
                        }
                }
        }

        val localMessages =
            inMemoryMessages.filter {
                it.conversationId == conversationId
            }

        if (localMessages.isNotEmpty()) {
            _messages.value = localMessages
        }
    }

    override suspend fun renameConversation(
        conversationId: String,
        newTitle: String
    ) {

        val title =
            newTitle.trim()

        if (title.isEmpty()) return

        val now =
            System.currentTimeMillis()

        _conversations.value =
            _conversations.value.map {
                if (it.id == conversationId) {
                    it.copy(
                        title = title,
                        updatedAt = now
                    )
                } else {
                    it
                }
            }

        if (
            _activeConversation.value?.id ==
            conversationId
        ) {
            _activeConversation.value =
                _activeConversation.value?.copy(
                    title = title,
                    updatedAt = now
                )
        }

        conversationDao?.updateConversationTitle(
            conversationId,
            title,
            now
        )
    }

    override suspend fun togglePinConversation(
        conversationId: String
    ) {

        val target =
            _conversations.value.find {
                it.id == conversationId
            } ?: return

        val pinned =
            !target.isPinned

        _conversations.value =
            _conversations.value
                .map {
                    if (it.id == conversationId) {
                        it.copy(
                            isPinned = pinned
                        )
                    } else {
                        it
                    }
                }
                .sortedWith(
                    compareByDescending<Conversation> {
                        it.isPinned
                    }.thenByDescending {
                        it.updatedAt
                    }
                )

        if (
            _activeConversation.value?.id ==
            conversationId
        ) {
            _activeConversation.value =
                _activeConversation.value?.copy(
                    isPinned = pinned
                )
        }

        conversationDao?.updateConversationPinned(
            conversationId,
            pinned
        )
    }

    override suspend fun toggleArchiveConversation(
        conversationId: String
    ) {

        val target =
            _conversations.value.find {
                it.id == conversationId
            } ?: return

        val archived =
            !target.isArchived

        _conversations.value =
            _conversations.value.map {
                if (it.id == conversationId) {
                    it.copy(
                        isArchived = archived
                    )
                } else {
                    it
                }
            }

        if (
            _activeConversation.value?.id ==
            conversationId
        ) {
            _activeConversation.value =
                _activeConversation.value?.copy(
                    isArchived = archived
                )
        }

        conversationDao?.updateConversationArchived(
            conversationId,
            archived
        )
    }

    override suspend fun deleteConversation(
        conversationId: String
    ) {

        cancelGeneration()

        _conversations.value =
            _conversations.value.filter {
                it.id != conversationId
            }

        inMemoryMessages.removeAll {
            it.conversationId == conversationId
        }

        conversationDao?.deleteConversation(
            conversationId
        )

        if (
            _activeConversation.value?.id ==
            conversationId
        ) {

            val remaining =
                _conversations.value

            if (remaining.isNotEmpty()) {
                selectConversation(
                    remaining.first().id
                )
            } else {
                startNewConversation()
            }
        }
    }

    override suspend fun clearMessages() {

        cancelGeneration()

        val conversation =
            _activeConversation.value ?: return

        _messages.value = emptyList()

        inMemoryMessages.removeAll {
            it.conversationId == conversation.id
        }

        conversationDao?.clearMessages(
            conversation.id
        )
    }

    override suspend fun cancelGeneration() {

        activeGenerationJob?.cancel()
        activeGenerationJob = null

        _isGenerating.value = false
        _searchPhase.value = SearchPhase.IDLE
    }

    // ---------------------------------------------------------
    // MEMORY
    // ---------------------------------------------------------

    override fun setMemoryEnabled(
        enabled: Boolean
    ) {

        memoryPreferences?.setMemoryEnabled(
            enabled
        ) ?: run {
            _isMemoryEnabled.value = enabled
        }
    }

    override suspend fun saveMemory(
        content: String,
        category: MemoryCategory
    ): MemoryItem {

        val trimmed =
            content.trim()

        val now =
            System.currentTimeMillis()

        val item =
            MemoryItem(
                id = UUID.randomUUID().toString(),
                content = trimmed,
                category = category,
                createdAt = now,
                updatedAt = now,
                enabled = true
            )

        _memories.value =
            listOf(item) +
                _memories.value.filter {
                    it.id != item.id
                }

        memoryDao?.insertMemory(
            MemoryItemEntity.fromDomain(item)
        )

        return item
    }

    override suspend fun toggleMemoryItemEnabled(
        memoryId: String,
        enabled: Boolean
    ) {

        val now =
            System.currentTimeMillis()

        _memories.value =
            _memories.value.map {
                if (it.id == memoryId) {
                    it.copy(
                        enabled = enabled,
                        updatedAt = now
                    )
                } else {
                    it
                }
            }

        memoryDao?.updateEnabled(
            memoryId,
            enabled,
            now
        )
    }

    override suspend fun deleteMemory(
        memoryId: String
    ) {

        _memories.value =
            _memories.value.filter {
                it.id != memoryId
            }

        memoryDao?.deleteMemoryById(
            memoryId
        )
    }

    override suspend fun clearAllMemories() {

        _memories.value = emptyList()

        memoryDao?.clearAllMemories()
    }

    // ---------------------------------------------------------
    // SEARCH
    // ---------------------------------------------------------

    override suspend fun searchConversations(
        query: String
    ): List<Conversation> {

        val q =
            query.trim()

        if (q.isEmpty()) {
            return _conversations.value
        }

        val titleMatches =
            _conversations.value.filter {
                it.title.contains(
                    q,
                    ignoreCase = true
                )
            }

        val matchingIds =
            mutableSetOf<String>()

        if (conversationDao != null) {

            try {

                val messages =
                    conversationDao.getAllMessages()

                messages
                    .filter {
                        it.content.contains(
                            q,
                            ignoreCase = true
                        )
                    }
                    .forEach {
                        matchingIds.add(
                            it.conversationId
                        )
                    }

            } catch (_: Exception) {
                // Ignore search DB failure.
            }
        }

        inMemoryMessages
            .filter {
                it.content.contains(
                    q,
                    ignoreCase = true
                )
            }
            .forEach {
                matchingIds.add(
                    it.conversationId
                )
            }

        return (
            titleMatches +
                _conversations.value.filter {
                    it.id in matchingIds
                }
            )
            .distinctBy {
                it.id
            }
            .sortedWith(
                compareByDescending<Conversation> {
                    it.isPinned
                }.thenByDescending {
                    it.updatedAt
                }
            )
    }

    // ---------------------------------------------------------
    // SEND MESSAGE
    // ---------------------------------------------------------

    override suspend fun sendMessage(
        content: String,
        config: AiModelConfig,
        attachments: List<Attachment>
    ): Result<ChatMessage> {

        val prompt =
            content.trim()

        if (
            prompt.isEmpty() &&
            attachments.isEmpty()
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Message or attachment cannot be empty."
                )
            )
        }

        cancelGeneration()

        /*
         * Remove old empty error placeholders.
         */
        _messages.value =
            _messages.value.filterNot {
                it.status == MessageStatus.ERROR &&
                    it.content.isBlank()
            }

        var conversation =
            _activeConversation.value

        if (conversation == null) {
            conversation =
                startNewConversation()
        }

        /*
         * Auto title.
         */
        if (
            conversation.title == "New Chat" &&
            _messages.value.none {
                it.conversationId ==
                    conversation.id
            }
        ) {

            val title =
                when {

                    prompt.isNotEmpty() ->
                        if (prompt.length > 32) {
                            prompt.take(29) + "…"
                        } else {
                            prompt
                        }

                    attachments.isNotEmpty() ->
                        attachments.first().name

                    else ->
                        "New Chat"
                }

            val updated =
                conversation.copy(
                    title = title,
                    updatedAt =
                        System.currentTimeMillis()
                )

            conversation = updated

            _activeConversation.value =
                updated

            _conversations.value =
                _conversations.value
                    .map {
                        if (it.id == updated.id) {
                            updated
                        } else {
                            it
                        }
                    }
                    .sortedWith(
                        compareByDescending<Conversation> {
                            it.isPinned
                        }.thenByDescending {
                            it.updatedAt
                        }
                    )

            conversationDao?.insertConversation(
                ConversationEntity.fromDomain(
                    updated
                )
            )
        }

        /*
         * Explicit memory requests only.
         */
        if (
            _isMemoryEnabled.value &&
            prompt.isNotEmpty()
        ) {

            val memory =
                MemoryDetector.extractMemory(
                    prompt
                )

            if (memory != null) {
                saveMemory(
                    memory.content,
                    memory.category
                )
            }
        }

        val userMessage =
            ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = conversation.id,
                role = MessageRole.USER,
                content = prompt,
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENT,
                attachments =
                    attachments.map {
                        it.metadata
                    }
            )

        inMemoryMessages.add(
            userMessage
        )

        _messages.value =
            _messages.value + userMessage

        persistMessage(
            userMessage,
            conversation
        )

        lastSentAttachments.clear()
        lastSentAttachments.addAll(
            attachments
        )

        /*
         * Image search.
         */
        val detector =
            searchIntentDetector as?
                DefaultSearchIntentDetector

        val isImageQuery =
            detector?.isImageSearch(prompt) == true

        var retrievedImages =
            emptyList<RetrievedImageResult>()

        if (isImageQuery) {

            _searchPhase.value =
                SearchPhase.SEARCHING

            val subject =
                detector?.extractImageSubject(
                    prompt
                ) ?: prompt

            retrievedImages =
                imageRetrievalService.searchImages(
                    subject,
                    maxResults = 2
                )
        }

        /*
         * Web search decision.
         */
        val decision =
            searchIntentDetector.detect(
                query = prompt,
                mode = config.searchMode,
                hasAttachments =
                    attachments.isNotEmpty()
            )

        val enableSearch =
            decision.needsSearch ||
                (
                    isImageQuery &&
                        retrievedImages.isEmpty()
                    )

        return executeAiStreaming(
            conversation = conversation,
            prompt = prompt,
            config = config,
            attachments = attachments,
            enableSearch = enableSearch,
            retrievedImages = retrievedImages
        )
    }

    // ---------------------------------------------------------
    // RETRY
    // ---------------------------------------------------------

    override suspend fun retryLastFailed(
        config: AiModelConfig
    ): Result<ChatMessage> {

        cancelGeneration()

        val current =
            _messages.value

        val errorIndex =
            current.indexOfLast {
                it.status == MessageStatus.ERROR
            }

        if (errorIndex < 0) {
            return Result.failure(
                IllegalStateException(
                    "No failed message found to retry."
                )
            )
        }

        /*
         * Find the latest USER message before the error.
         */
        val userMessage =
            current
                .take(errorIndex)
                .lastOrNull {
                    it.role == MessageRole.USER &&
                        it.status == MessageStatus.SENT
                }
                ?: return Result.failure(
                    IllegalStateException(
                        "No preceding user prompt found."
                    )
                )

        /*
         * Remove ONLY the error message.
         * Keep the original user message.
         */
        _messages.value =
            current.filterIndexed {
                index, _ ->
                index != errorIndex
            }

        val conversation =
            _activeConversation.value
                ?: startNewConversation()

        val detector =
            searchIntentDetector as?
                DefaultSearchIntentDetector

        val isImageQuery =
            detector?.isImageSearch(
                userMessage.content
            ) == true

        var retrievedImages =
            emptyList<RetrievedImageResult>()

        if (isImageQuery) {

            _searchPhase.value =
                SearchPhase.SEARCHING

            val subject =
                detector?.extractImageSubject(
                    userMessage.content
                ) ?: userMessage.content

            retrievedImages =
                imageRetrievalService.searchImages(
                    subject,
                    maxResults = 2
                )
        }

        val decision =
            searchIntentDetector.detect(
                query = userMessage.content,
                mode = config.searchMode,
                hasAttachments =
                    userMessage.attachments.isNotEmpty()
            )

        val enableSearch =
            decision.needsSearch ||
                (
                    isImageQuery &&
                        retrievedImages.isEmpty()
                    )

        val retryAttachments =
            if (
                userMessage.attachments.isNotEmpty()
            ) {
                lastSentAttachments.toList()
            } else {
                emptyList()
            }

        return executeAiStreaming(
            conversation = conversation,
            prompt = userMessage.content,
            config = config,
            attachments = retryAttachments,
            enableSearch = enableSearch,
            retrievedImages = retrievedImages
        )
    }

    // ---------------------------------------------------------
    // REAL AI STREAMING
    // ---------------------------------------------------------

    private suspend fun executeAiStreaming(
        conversation: Conversation,
        prompt: String,
        config: AiModelConfig,
        attachments: List<Attachment> = emptyList(),
        enableSearch: Boolean = false,
        retrievedImages: List<RetrievedImageResult> =
            emptyList()
    ): Result<ChatMessage> = coroutineScope {

        _isGenerating.value = true

        _searchPhase.value =
            if (enableSearch) {
                SearchPhase.SEARCHING
            } else {
                SearchPhase.IDLE
            }

        val assistantId =
            UUID.randomUUID().toString()

        val text =
            StringBuilder()

        val sources =
            mutableListOf<SearchSource>()

        var assistantVisible =
            false

        var completedMessage:
            ChatMessage? = null

        var failure:
            Throwable? = null

        /*
         * Add retrieved image source information.
         */
        retrievedImages.forEach { image ->

            if (
                sources.none {
                    it.url == image.sourceUrl
                }
            ) {

                sources.add(
                    SearchSource(
                        title =
                            "${image.title} (Wikimedia Commons)",
                        url =
                            image.sourceUrl,
                        snippet =
                            "Public image retrieved from ${image.attribution}"
                    )
                )
            }
        }

        activeGenerationJob =
            coroutineContext[Job]

        try {

            /*
             * Only useful conversation history.
             *
             * IMPORTANT:
             * ERROR messages are NEVER sent back to Gemini.
             */
            val history =
                _messages.value
                    .filter {
                        it.conversationId ==
                            conversation.id
                    }
                    .filter {
                        it.status != MessageStatus.ERROR
                    }
                    .filter {
                        it.content.isNotBlank()
                    }
                    .takeLast(20)

            /*
             * Memory context.
             */
            val effectiveConfig =
                if (_isMemoryEnabled.value) {

                    val memories =
                        _memories.value.filter {
                            it.enabled
                        }

                    if (memories.isNotEmpty()) {

                        val context =
                            buildMemoryContext(
                                memories,
                                prompt
                            )

                        config.copy(
                            systemPrompt =
                                if (
                                    config.systemPrompt
                                        .isNotBlank()
                                ) {
                                    config.systemPrompt +
                                        "\n\n" +
                                        context
                                } else {
                                    context
                                }
                        )

                    } else {
                        config
                    }

                } else {
                    config
                }

            /*
             * Image instructions.
             */
            val imageInstructions =
                if (
                    retrievedImages.isNotEmpty()
                ) {

                    val imageContext =
                        retrievedImages.joinToString(
                            "\n"
                        ) { image ->

                            "- Title: ${image.title}, " +
                                "Image URL: ${image.imageUrl}, " +
                                "Source: ${image.sourceUrl}"
                        }

                    """

[RETRIEVED IMAGES]

$imageContext

Rules:
- Show the retrieved image using markdown when appropriate.
- Never claim that a retrieved image was AI-generated.
- Clearly identify it as a web/public image.
- Keep the source link.
""".trimIndent()

                } else if (
                    isImageRequest(prompt)
                ) {

                    """

[IMAGE RETRIEVAL]

No suitable free public image was found.
Do not invent an image URL.
Do not pretend that an image was generated.
""".trimIndent()

                } else {
                    ""
                }

            val finalConfig =
                if (
                    imageInstructions.isNotBlank()
                ) {
                    effectiveConfig.copy(
                        systemPrompt =
                            effectiveConfig.systemPrompt +
                                "\n\n" +
                                imageInstructions
                    )
                } else {
                    effectiveConfig
                }

            /*
             * REAL Gemini streaming call.
             */
            aiService
                .generateStream(
                    conversationId =
                        conversation.id,
                    prompt = prompt,
                    history = history,
                    config = finalConfig,
                    attachments = attachments,
                    enableSearch = enableSearch
                )
                .collect { chunk ->

                    if (chunk.isSearching) {
                        _searchPhase.value =
                            SearchPhase.SEARCHING
                    }

                    if (
                        chunk.searchQueries.isNotEmpty() ||
                        chunk.searchSources.isNotEmpty()
                    ) {
                        _searchPhase.value =
                            SearchPhase.READING_SOURCES
                    }

                    chunk.searchSources.forEach { source ->

                        if (
                            sources.none {
                                it.url == source.url
                            }
                        ) {
                            sources.add(source)
                        }
                    }

                    if (
                        !chunk.isComplete &&
                        chunk.textDelta.isNotEmpty()
                    ) {

                        _searchPhase.value =
                            SearchPhase.GENERATING

                        text.append(
                            chunk.textDelta
                        )

                        val current =
                            text.toString()

                        if (!assistantVisible) {

                            assistantVisible = true

                            val message =
                                ChatMessage(
                                    id = assistantId,
                                    conversationId =
                                        conversation.id,
                                    role =
                                        MessageRole.ASSISTANT,
                                    content = current,
                                    timestamp =
                                        System.currentTimeMillis(),
                                    status =
                                        MessageStatus.SENDING,
                                    searchSources =
                                        sources.toList()
                                )

                            _messages.value =
                                _messages.value +
                                    message

                        } else {

                            _messages.value =
                                _messages.value.map {
                                    if (
                                        it.id ==
                                            assistantId
                                    ) {
                                        it.copy(
                                            content =
                                                current,
                                            searchSources =
                                                sources.toList()
                                        )
                                    } else {
                                        it
                                    }
                                }
                        }
                    }

                    if (chunk.isComplete) {

                        _searchPhase.value =
                            SearchPhase.IDLE

                        var finalContent =
                            text.toString().trim()

                        /*
                         * Append retrieved images only
                         * when Gemini did not already include them.
                         */
                        if (
                            retrievedImages.isNotEmpty() &&
                            !finalContent.contains(
                                "!["
                            )
                        ) {

                            val appendix =
                                buildString {

                                    append("\n\n")

                                    retrievedImages
                                        .forEach { image ->

                                            append(
                                                "![${image.title}](" +
                                                    "${image.imageUrl})\n"
                                            )

                                            append(
                                                "*Public image from " +
                                                    "${image.attribution} • " +
                                                    "[View source](" +
                                                    "${image.sourceUrl})*\n\n"
                                            )
                                        }
                                }

                            finalContent =
                                (
                                    finalContent +
                                        appendix
                                    ).trim()
                        }

                        /*
                         * Never save an empty assistant message
                         * as a successful answer.
                         */
                        if (finalContent.isBlank()) {
                            throw IllegalStateException(
                                "Mayra AI returned an empty response."
                            )
                        }

                        val finalMessage =
                            ChatMessage(
                                id = assistantId,
                                conversationId =
                                    conversation.id,
                                role =
                                    MessageRole.ASSISTANT,
                                content =
                                    finalContent,
                                timestamp =
                                    System.currentTimeMillis(),
                                status =
                                    MessageStatus.SENT,
                                searchSources =
                                    sources.toList()
                            )

                        completedMessage =
                            finalMessage

                        inMemoryMessages.add(
                            finalMessage
                        )

                        _messages.value =
                            _messages.value.map {
                                if (
                                    it.id ==
                                        assistantId
                                ) {
                                    finalMessage
                                } else {
                                    it
                                }
                            }

                        persistMessage(
                            finalMessage,
                            conversation
                        )
                    }
                }

        } catch (e: CancellationException) {

            /*
             * User cancelled generation.
             *
             * Do NOT save a fake successful answer.
             */
            _messages.value =
                _messages.value.filter {
                    it.id != assistantId
                }

            throw e

        } catch (e: Exception) {

            failure = e

            /*
             * IMPORTANT:
             *
             * Do not save the partial AI response as an
             * assistant ERROR message.
             *
             * This prevents failed/stale AI text from being
             * sent to Gemini in the next request.
             */
            _messages.value =
                _messages.value.filter {
                    it.id != assistantId
                }

        } finally {

            _isGenerating.value = false
            _searchPhase.value = SearchPhase.IDLE
            activeGenerationJob = null
        }

        /*
         * Return the real error to the UI.
         */
        if (failure != null) {

            Result.failure(
                failure!!
            )

        } else {

            completedMessage?.let {
                Result.success(it)
            } ?: Result.failure(
                IllegalStateException(
                    "No response was created."
                )
            )
        }
    }

    // ---------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------

    private fun isImageRequest(
        prompt: String
    ): Boolean {

        return (
            prompt.contains(
                "image",
                ignoreCase = true
            ) ||
                prompt.contains(
                    "photo",
                    ignoreCase = true
                ) ||
                prompt.contains(
                    "picture",
                    ignoreCase = true
                ) ||
                prompt.contains(
                    "ছবি"
                ) ||
                prompt.contains(
                    "ফটো"
                ) ||
                prompt.contains(
                    "चित्र"
                )
            )
    }

    private fun buildMemoryContext(
        memories: List<MemoryItem>,
        prompt: String
    ): String {

        val relevant =
            selectRelevantMemories(
                memories,
                prompt
            )

        val builder =
            StringBuilder(
                "[User Profile & Context Memories]:\n"
            )

        relevant.forEach { memory ->

            builder.append(
                "- [${memory.category.displayName}]: " +
                    "${memory.content}\n"
            )
        }

        builder.append(
            "Apply these memories only when relevant."
        )

        return builder.toString()
    }

    private fun selectRelevantMemories(
        memories: List<MemoryItem>,
        prompt: String,
        maxCount: Int = 10
    ): List<MemoryItem> {

        if (memories.size <= maxCount) {
            return memories
        }

        val words =
            prompt
                .lowercase(Locale.ROOT)
                .split(Regex("\\s+"))
                .filter {
                    it.length > 2
                }

        return memories
            .sortedByDescending { memory ->

                val lower =
                    memory.content
                        .lowercase(Locale.ROOT)

                var score = 0

                words.forEach { word ->

                    if (
                        lower.contains(word)
                    ) {
                        score += 2
                    }
                }

                if (
                    memory.category ==
                        MemoryCategory.PREFERENCE ||
                    memory.category ==
                        MemoryCategory.INSTRUCTION
                ) {
                    score += 1
                }

                score
            }
            .take(maxCount)
    }

    private suspend fun persistMessage(
        message: ChatMessage,
        conversation: Conversation
    ) {

        val dao =
            conversationDao
                ?: return

        try {

            if (
                dao.getConversationById(
                    conversation.id
                ) == null
            ) {
                dao.insertConversation(
                    ConversationEntity.fromDomain(
                        conversation
                    )
                )
            }

            val preview =
                if (message.content.length > 50) {
                    message.content.take(47) + "…"
                } else {
                    message.content
                }

            dao.saveMessageAndUpdateConversation(
                ChatMessageEntity.fromDomain(
                    message
                ),
                updatedAt =
                    System.currentTimeMillis(),
                preview = preview
            )

        } catch (_: Exception) {
            /*
             * Database failure must never stop AI streaming.
             */
        }
    }
}
