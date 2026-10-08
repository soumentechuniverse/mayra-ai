package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.service.ImageGenerationService
import com.example.data.service.OpenAIService
import com.example.domain.model.AiModelConfig
import com.example.domain.model.AiStreamChunk
import com.example.domain.model.Attachment
import com.example.domain.model.AttachmentMetadata
import com.example.domain.model.AttachmentType
import com.example.domain.model.ChatMessage
import com.example.domain.model.MessageRole
import com.example.domain.model.MessageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar
import java.util.UUID

class ChatRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) : ChatRepository {

    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()

    private val openAIService = OpenAIService()
    private val imageGenerationService = ImageGenerationService()

    private val json = Json {
        ignoreUnknownKeys = true
    }

    companion object {

        const val PRIMARY_MODEL = "gpt-6-luna"

        private const val SYSTEM_PROMPT = """
You are Mayra AI, an intelligent, fast, accurate and highly capable multilingual AI assistant developed by Soumen Mondal.

Core Instructions:

1. Reply in the same language as the user's latest message unless another language is requested.

2. Understand Bengali, English, Hindi, Urdu, Banglish, Hinglish and other languages naturally.

3. When replying in Bengali, use natural Indian/West Bengal Bengali.

4. Answer simple questions directly and quickly.

5. For complex questions, provide the necessary detail.

6. Never invent current dates, times, news, prices, scores, weather or other changing information.

7. When web search is enabled, use current information carefully.

8. Analyze uploaded images and supported files when they are actually provided.

9. Never guess or claim the real-world identity of a person in an image.

10. Use clean Markdown when useful.

11. Be honest about limitations and API errors.

12. Do not expose hidden chain-of-thought.

13. Use natural emojis only when appropriate.

14. Prefer accuracy, usefulness and direct answers over filler.
"""
    }

    override fun getConversations(): Flow<List<ConversationEntity>> =
        conversationDao.getActiveConversations()

    override fun getArchivedConversations(): Flow<List<ConversationEntity>> =
        conversationDao.getArchivedConversations()

    override fun searchConversations(
        query: String
    ): Flow<List<ConversationEntity>> =
        conversationDao.searchConversations(query)

    override suspend fun getConversationById(
        id: String
    ): ConversationEntity? =
        withContext(Dispatchers.IO) {
            conversationDao.getConversationById(id)
        }

    override suspend fun createConversation(
        title: String
    ): ConversationEntity =
        withContext(Dispatchers.IO) {

            val conversation = ConversationEntity(
                id = UUID.randomUUID().toString(),
                title = title.ifBlank {
                    "New Conversation"
                },
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            conversationDao.insertOrUpdate(conversation)

            conversation
        }

    override suspend fun updateConversationTitle(
        id: String,
        newTitle: String
    ) = withContext(Dispatchers.IO) {

        val existing =
            conversationDao.getConversationById(id)
                ?: return@withContext

        conversationDao.update(
            existing.copy(
                title = newTitle,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun togglePinConversation(
        id: String,
        isPinned: Boolean
    ) = withContext(Dispatchers.IO) {

        val existing =
            conversationDao.getConversationById(id)
                ?: return@withContext

        conversationDao.update(
            existing.copy(
                isPinned = isPinned,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun toggleArchiveConversation(
        id: String,
        isArchived: Boolean
    ) = withContext(Dispatchers.IO) {

        val existing =
            conversationDao.getConversationById(id)
                ?: return@withContext

        conversationDao.update(
            existing.copy(
                isArchived = isArchived,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteConversation(
        id: String
    ) = withContext(Dispatchers.IO) {

        messageDao.deleteMessagesForConversation(id)
        conversationDao.deleteById(id)
    }

    override fun getMessagesForConversation(
        conversationId: String
    ): Flow<List<MessageEntity>> =
        messageDao.getMessagesForConversation(conversationId)

    override suspend fun deleteMessage(
        messageId: String
    ) = withContext(Dispatchers.IO) {

        val message =
            messageDao.getMessageById(messageId)
                ?: return@withContext

        messageDao.delete(message)
    }

    private fun isImageGenerationRequest(
        prompt: String
    ): Boolean {

        val clean = prompt.trim().lowercase()

        val english = listOf(
            "generate image",
            "generate an image",
            "generate a picture",
            "generate a photo",
            "create an image",
            "create image",
            "create a picture",
            "create a photo",
            "make an image",
            "make a picture",
            "make a photo",
            "draw ",
            "paint "
        )

        val bengali = listOf(
            "ছবি তৈরি কর",
            "ছবি তৈরি করে",
            "ছবি বানাও",
            "ছবি বানিয়ে",
            "ছবি আঁকো",
            "ইমেজ তৈরি কর",
            "ইমেজ বানাও",
            "চিত্র আঁকো",
            "জেনারেট কর"
        )

        val hindi = listOf(
            "तस्वीर बनाओ",
            "तस्वीर बनाना",
            "इमेज बनाओ",
            "इमेज बनाना",
            "चित्र बनाओ",
            "फोटो बनाओ"
        )

        return english.any {
            clean.contains(it)
        } ||
                bengali.any {
                    clean.contains(it)
                } ||
                hindi.any {
                    clean.contains(it)
                }
    }

    private fun needsWebSearch(
        prompt: String
    ): Boolean {

        val clean = prompt.trim().lowercase()

        val keywords = listOf(

            "latest",
            "today's news",
            "today news",
            "yesterday",
            "news",
            "current",
            "weather",
            "stock price",
            "share price",
            "price of",
            "who won",
            "score",
            "schedule",
            "recent",
            "upcoming",
            "live",
            "search the web",
            "google for",
            "who is the current",
            "what happened in",

            "সর্বশেষ",
            "সাম্প্রতিক",
            "বর্তমান",
            "আজকের খবর",
            "আজকের আবহাওয়া",
            "আজকের আবহাওয়া",
            "আজকের দাম",
            "লাইভ",
            "স্কোর",
            "ফলাফল",
            "আগামী",
            "খবর",
            "ওয়েবে খুঁজে",
            "ওয়েবে খুঁজে",
            "খুঁজে দেখ",

            "ताज़ा खबर",
            "ताजा खबर",
            "आज की खबर",
            "आज का मौसम",
            "वर्तमान",
            "नवीनतम",
            "लेटेस्ट",
            "लाइव",
            "स्कोर",
            "परिणाम",
            "मौसम",
            "कीमत"
        )

        return keywords.any {
            clean.contains(it)
        }
    }

    private fun localDateTimeAnswer(
        prompt: String
    ): String? {

        val clean = prompt.trim().lowercase()

        val dateRequest =
            clean.contains("today's date") ||
                    clean.contains("today date") ||
                    clean.contains("what is the date today") ||
                    clean.contains("what date is it") ||
                    clean.contains("current date") ||
                    clean.contains("আজকের তারিখ") ||
                    clean.contains("আজকে তারিখ") ||
                    clean.contains("আজ কত তারিখ") ||
                    clean.contains("আজকের দিন") ||
                    clean.contains("আজ কোন তারিখ") ||
                    clean.contains("আজকে কোন তারিখ") ||
                    clean.contains("आज की तारीख") ||
                    clean.contains("आज कौन सी तारीख")

        val timeRequest =
            clean.contains("what time is it") ||
                    clean.contains("current time") ||
                    clean.contains("time now") ||
                    clean.contains("what is the time") ||
                    clean.contains("এখন কয়টা বাজে") ||
                    clean.contains("এখন কয়টা বাজে") ||
                    clean.contains("এখন সময় কত") ||
                    clean.contains("এখন সময় কত") ||
                    clean.contains("বর্তমান সময়") ||
                    clean.contains("বর্তমান সময়") ||
                    clean.contains("अभी कितने बजे") ||
                    clean.contains("अभी समय क्या है")

        if (!dateRequest && !timeRequest) {
            return null
        }

        val calendar = Calendar.getInstance()

        val day =
            calendar.get(Calendar.DAY_OF_MONTH)

        val month =
            calendar.get(Calendar.MONTH) + 1

        val year =
            calendar.get(Calendar.YEAR)

        val hour =
            calendar.get(Calendar.HOUR)

        val minute =
            calendar.get(Calendar.MINUTE)

        val amPm =
            if (calendar.get(Calendar.AM_PM) == Calendar.AM) {
                "AM"
            } else {
                "PM"
            }

        val displayHour =
            if (hour == 0) 12 else hour

        return when {

            dateRequest && timeRequest ->
                "আজকের তারিখ: %02d/%02d/%04d\nবর্তমান সময়: %02d:%02d %s"
                    .format(
                        day,
                        month,
                        year,
                        displayHour,
                        minute,
                        amPm
                    )

            dateRequest ->
                "আজকের তারিখ: %02d/%02d/%04d"
                    .format(
                        day,
                        month,
                        year
                    )

            else ->
                "বর্তমান সময়: %02d:%02d %s"
                    .format(
                        displayHour,
                        minute,
                        amPm
                    )
        }
    }

    override suspend fun sendMessage(
        conversationId: String,
        userPrompt: String,
        attachmentBytes: ByteArray?,
        attachmentMimeType: String?,
        attachmentName: String?,
        attachmentUriString: String?
    ): Flow<StreamEvent> = flow {

        val userMessageId =
            UUID.randomUUID().toString()

        val assistantMessageId =
            UUID.randomUUID().toString()

        val now =
            System.currentTimeMillis()

        val userMessage =
            MessageEntity(
                id = userMessageId,
                conversationId = conversationId,
                role = "user",
                content = userPrompt,
                timestamp = now,
                status = "SENT",
                attachmentUri = attachmentUriString,
                attachmentMimeType = attachmentMimeType,
                attachmentName = attachmentName
            )

        messageDao.insertOrUpdate(userMessage)

        val conversation =
            conversationDao.getConversationById(
                conversationId
            )

        if (
            conversation != null &&
            (
                conversation.title == "New Conversation" ||
                        conversation.title == "New Chat"
                )
        ) {

            val autoTitle =
                if (userPrompt.isNotBlank()) {

                    userPrompt
                        .take(30)
                        .trim() +
                            if (userPrompt.length > 30) {
                                "..."
                            } else {
                                ""
                            }

                } else if (
                    !attachmentName.isNullOrBlank()
                ) {

                    attachmentName.take(30)

                } else {

                    "Conversation"
                }

            conversationDao.update(
                conversation.copy(
                    title = autoTitle,
                    updatedAt = now
                )
            )

        } else if (conversation != null) {

            conversationDao.update(
                conversation.copy(
                    updatedAt = now
                )
            )
        }

        val localAnswer =
            localDateTimeAnswer(userPrompt)

        if (
            localAnswer != null &&
            attachmentBytes == null
        ) {

            val localMessage =
                MessageEntity(
                    id = assistantMessageId,
                    conversationId = conversationId,
                    role = "assistant",
                    content = localAnswer,
                    timestamp = now + 1,
                    status = "SENT"
                )

            messageDao.insertOrUpdate(localMessage)

            emit(
                StreamEvent.TextChunk(
                    localAnswer
                )
            )

            emit(
                StreamEvent.Completed(
                    localAnswer,
                    emptyList()
                )
            )

            return@flow
        }

        if (
            isImageGenerationRequest(userPrompt)
        ) {

            handleImageGeneration(
                conversationId = conversationId,
                assistantMessageId = assistantMessageId,
                userPrompt = userPrompt,
                collector = this
            )

            return@flow
        }

        val assistantMessage =
            MessageEntity(
                id = assistantMessageId,
                conversationId = conversationId,
                role = "assistant",
                content = "",
                timestamp = now + 1,
                status = "SENDING"
            )

        messageDao.insertOrUpdate(
            assistantMessage
        )

        val historyEntities =
            messageDao
                .getMessagesList(conversationId)
                .filter {
                    it.id != userMessageId &&
                            it.id != assistantMessageId &&
                            it.status != "ERROR"
                }
                .takeLast(10)

        val history =
            historyEntities.map {
                messageEntityToChatMessage(
                    conversationId = conversationId,
                    message = it
                )
            }

        val attachments =
            buildAttachments(
                bytes = attachmentBytes,
                mimeType = attachmentMimeType,
                name = attachmentName,
                uri = attachmentUriString
            )

        executeOpenAIStream(
            conversationId = conversationId,
            assistantMessageId = assistantMessageId,
            prompt = userPrompt,
            history = history,
            attachments = attachments,
            enableSearch = needsWebSearch(userPrompt),
            collector = this
        )

    }.flowOn(Dispatchers.IO)

    override suspend fun retryMessage(
        conversationId: String,
        failedMessageId: String
    ): Flow<StreamEvent> = flow {

        val failedMessage =
            messageDao.getMessageById(
                failedMessageId
            ) ?: return@flow

        val messages =
            messageDao.getMessagesList(
                conversationId
            )

        val failedIndex =
            messages.indexOfFirst {
                it.id == failedMessageId
            }

        val userMessage =
            if (failedIndex > 0) {
                messages[failedIndex - 1]
            } else {
                null
            }

        val prompt =
            userMessage?.content ?: "Hello"

        val attachmentBytes =
            if (
                !userMessage?.attachmentUri.isNullOrBlank()
            ) {

                try {

                    context.contentResolver
                        .openInputStream(
                            Uri.parse(
                                userMessage?.attachmentUri
                            )
                        )
                        ?.use {
                            it.readBytes()
                        }

                } catch (_: Exception) {

                    null
                }

            } else {

                null
            }

        messageDao.insertOrUpdate(
            failedMessage.copy(
                content = "",
                status = "SENDING"
            )
        )

        val historyEntities =
            if (failedIndex > 0) {

                messages
                    .take(failedIndex - 1)
                    .filter {
                        it.status != "ERROR"
                    }
                    .takeLast(10)

            } else {

                emptyList()
            }

        val history =
            historyEntities.map {
                messageEntityToChatMessage(
                    conversationId = conversationId,
                    message = it
                )
            }

        val attachments =
            buildAttachments(
                bytes = attachmentBytes,
                mimeType =
                    userMessage?.attachmentMimeType,
                name =
                    userMessage?.attachmentName,
                uri =
                    userMessage?.attachmentUri
            )

        executeOpenAIStream(
            conversationId = conversationId,
            assistantMessageId = failedMessageId,
            prompt = prompt,
            history = history,
            attachments = attachments,
            enableSearch = needsWebSearch(prompt),
            collector = this
        )

    }.flowOn(Dispatchers.IO)

    private fun messageEntityToChatMessage(
        conversationId: String,
        message: MessageEntity
    ): ChatMessage {

        val role =
            when (message.role.lowercase()) {

                "assistant" ->
                    MessageRole.ASSISTANT

                "system" ->
                    MessageRole.SYSTEM

                else ->
                    MessageRole.USER
            }

        val status =
            when (message.status) {

                "ERROR" ->
                    MessageStatus.ERROR

                "SENDING" ->
                    MessageStatus.SENDING

                else ->
                    MessageStatus.SENT
            }

        val attachmentMetadata =
            if (
                !message.attachmentName.isNullOrBlank()
            ) {

                listOf(
                    AttachmentMetadata(
                        name =
                            message.attachmentName,
                        mimeType =
                            message.attachmentMimeType
                                ?: "application/octet-stream",
                        type =
                            attachmentTypeFromMime(
                                message.attachmentMimeType
                            ),
                        localUri =
                            message.attachmentUri
                    )
                )

            } else {

                emptyList()
            }

        return ChatMessage(
            id = message.id,
            conversationId = conversationId,
            role = role,
            content = message.content,
            timestamp = message.timestamp,
            status = status,
            attachments = attachmentMetadata
        )
    }

    private fun buildAttachments(
        bytes: ByteArray?,
        mimeType: String?,
        name: String?,
        uri: String?
    ): List<Attachment> {

        if (bytes == null) {
            return emptyList()
        }

        val actualMimeType =
            mimeType
                ?.takeIf { it.isNotBlank() }
                ?: "application/octet-stream"

        val actualName =
            name
                ?.takeIf { it.isNotBlank() }
                ?: "attachment"

        val base64 =
            Base64.encodeToString(
                bytes,
                Base64.NO_WRAP
            )

        val type =
            attachmentTypeFromMime(
                actualMimeType
            )

        val textContent =
            if (
                type == AttachmentType.DOCUMENT &&
                (
                    actualMimeType.startsWith("text/") ||
                            actualMimeType.contains("json") ||
                            actualMimeType.contains("csv") ||
                            actualMimeType.contains("markdown")
                    )
            ) {

                try {
                    String(
                        bytes,
                        Charsets.UTF_8
                    )
                } catch (_: Exception) {
                    null
                }

            } else {

                null
            }

        return listOf(
            Attachment(
                name = actualName,
                mimeType = actualMimeType,
                sizeBytes = bytes.size.toLong(),
                type = type,
                localUri = uri,
                base64Data = base64,
                textContent = textContent
            )
        )
    }

    private fun attachmentTypeFromMime(
        mimeType: String?
    ): AttachmentType {

        val mime =
            mimeType
                ?.lowercase()
                .orEmpty()

        return when {

            mime.startsWith("image/") ->
                AttachmentType.IMAGE

            mime == "application/pdf" ->
                AttachmentType.PDF

            else ->
                AttachmentType.DOCUMENT
        }
    }

    private suspend fun executeOpenAIStream(
        conversationId: String,
        assistantMessageId: String,
        prompt: String,
        history: List<ChatMessage>,
        attachments: List<Attachment>,
        enableSearch: Boolean,
        collector: FlowCollector<StreamEvent>
    ) {

        val fullText =
            StringBuilder()

        var receivedComplete =
            false

        try {

            val config =
                AiModelConfig(
                    modelId = PRIMARY_MODEL,
                    displayName = "Mayra",
                    description =
                        "OpenAI-powered Mayra AI",
                    temperature = 0.7f,
                    maxTokens = 8192,
                    systemPrompt =
                        SYSTEM_PROMPT
                )

            openAIService
                .generateStream(
                    conversationId =
                        conversationId,
                    prompt =
                        prompt,
                    history =
                        history,
                    config =
                        config,
                    attachments =
                        attachments,
                    enableSearch =
                        enableSearch
                )
                .collect { chunk: AiStreamChunk ->

                    if (chunk.textDelta.isNotEmpty()) {

                        fullText.append(
                            chunk.textDelta
                        )

                        collector.emit(
                            StreamEvent.TextChunk(
                                chunk.textDelta
                            )
                        )

                        messageDao.insertOrUpdate(
                            MessageEntity(
                                id =
                                    assistantMessageId,
                                conversationId =
                                    conversationId,
                                role =
                                    "assistant",
                                content =
                                    fullText.toString(),
                                timestamp =
                                    System.currentTimeMillis(),
                                status =
                                    "SENDING"
                            )
                        )
                    }

                    if (chunk.isComplete) {
                        receivedComplete = true
                    }
                }

            val finalText =
                fullText
                    .toString()
                    .trim()

            if (finalText.isEmpty()) {

                val error =
                    "Mayra AI did not return a response. Please try again."

                messageDao.insertOrUpdate(
                    MessageEntity(
                        id =
                            assistantMessageId,
                        conversationId =
                            conversationId,
                        role =
                            "assistant",
                        content =
                            error,
                        timestamp =
                            System.currentTimeMillis(),
                        status =
                            "ERROR"
                    )
                )

                collector.emit(
                    StreamEvent.Error(
                        error,
                        isRetryable = true
                    )
                )

                return
            }

            messageDao.insertOrUpdate(
                MessageEntity(
                    id =
                        assistantMessageId,
                    conversationId =
                        conversationId,
                    role =
                        "assistant",
                    content =
                        finalText,
                    timestamp =
                        System.currentTimeMillis(),
                    status =
                        "SENT"
                )
            )

            collector.emit(
                StreamEvent.Completed(
                    finalText,
                    emptyList()
                )
            )

        } catch (e: Exception) {

            val errorMessage =
                e.message
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "OpenAI service error. Please try again."

            messageDao.insertOrUpdate(
                MessageEntity(
                    id =
                        assistantMessageId,
                    conversationId =
                        conversationId,
                    role =
                        "assistant",
                    content =
                        errorMessage,
                    timestamp =
                        System.currentTimeMillis(),
                    status =
                        "ERROR"
                )
            )

            collector.emit(
                StreamEvent.Error(
                    errorMessage,
                    isRetryable = true
                )
            )
        }
    }

    private suspend fun handleImageGeneration(
        conversationId: String,
        assistantMessageId: String,
        userPrompt: String,
        collector: FlowCollector<StreamEvent>
    ) {

        val now =
            System.currentTimeMillis()

        val placeholder =
            MessageEntity(
                id =
                    assistantMessageId,
                conversationId =
                    conversationId,
                role =
                    "assistant",
                content =
                    "Generating your image...",
                timestamp =
                    now + 1,
                status =
                    "SENDING",
                isGeneratedImage =
                    true
            )

        messageDao.insertOrUpdate(
            placeholder
        )

        try {

            val cleanPrompt =
                cleanImagePrompt(
                    userPrompt
                )

            val result =
                imageGenerationService
                    .generateImage(
                        prompt =
                            cleanPrompt,
                        aspectRatio =
                            "1:1",
                        imageSize =
                            "1K"
                    )
                    .getOrThrow()

            val imageUri =
                saveGeneratedImage(
                    messageId =
                        assistantMessageId,
                    base64Data =
                        result.base64Data,
                    mimeType =
                        result.mimeType
                )

            val text =
                result.text
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "Here is the generated image."

            messageDao.insertOrUpdate(
                placeholder.copy(
                    content =
                        text,
                    imageUrl =
                        imageUri,
                    status =
                        "SENT",
                    isGeneratedImage =
                        true
                )
            )

            collector.emit(
                StreamEvent.TextChunk(
                    text
                )
            )

            collector.emit(
                StreamEvent.Completed(
                    text,
                    emptyList()
                )
            )

        } catch (e: Exception) {

            val error =
                "Could not generate image: ${
                    e.message ?: "Unknown error"
                }"

            messageDao.insertOrUpdate(
                placeholder.copy(
                    content =
                        error,
                    status =
                        "ERROR"
                )
            )

            collector.emit(
                StreamEvent.Error(
                    error,
                    isRetryable = true
                )
            )
        }
    }

    private fun cleanImagePrompt(
        prompt: String
    ): String {

        var clean =
            prompt.trim()

        val prefixes =
            listOf(

                Regex(
                    "(?i)^generate\\s+(an?\\s+)?(image|picture|photo)\\s*(of)?\\s*"
                ),

                Regex(
                    "(?i)^create\\s+(an?\\s+)?(image|picture|photo)\\s*(of)?\\s*"
                ),

                Regex(
                    "(?i)^make\\s+(an?\\s+)?(image|picture|photo)\\s*(of)?\\s*"
                ),

                Regex(
                    "(?i)^draw\\s+"
                ),

                Regex(
                    "(?i)^paint\\s+"
                ),

                Regex(
                    "(?i)^generate\\s+"
                ),

                Regex(
                    "(?i)^create\\s+"
                ),

                Regex(
                    "(?i)^make\\s+"
                )
            )

        for (regex in prefixes) {
            clean =
                clean.replaceFirst(
                    regex,
                    ""
                )
        }

        clean =
            clean
                .replaceFirst(
                    Regex(
                        "^ছবি\\s*(তৈরি কর|বানাও|আঁকো)\\s*"
                    ),
                    ""
                )
                .replaceFirst(
                    Regex(
                        "^ইমেজ\\s*(তৈরি কর|বানাও)\\s*"
                    ),
                    ""
                )
                .replaceFirst(
                    Regex(
                        "^तस्वीर\\s*बनाओ\\s*"
                    ),
                    ""
                )
                .trim()

        return clean.ifBlank {
            "A beautiful cinematic scene"
        }
    }

    private suspend fun saveGeneratedImage(
        messageId: String,
        base64Data: String,
        mimeType: String
    ): String =
        withContext(Dispatchers.IO) {

            val directory =
                File(
                    context.filesDir,
                    "generated_images"
                )

            if (!directory.exists()) {
                directory.mkdirs()
            }

            val extension =
                when {

                    mimeType.contains("jpeg") ||
                            mimeType.contains("jpg") ->
                        "jpg"

                    mimeType.contains("webp") ->
                        "webp"

                    else ->
                        "png"
                }

            val file =
                File(
                    directory,
                    "$messageId.$extension"
                )

            val bytes =
                Base64.decode(
                    base64Data,
                    Base64.DEFAULT
                )

            file.writeBytes(bytes)

            Uri.fromFile(file).toString()
        }
}
