package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.remote.RetrofitClient
import com.example.data.remote.model.Candidate
import com.example.data.remote.model.Content
import com.example.data.remote.model.GenerateContentRequest
import com.example.data.remote.model.GenerationConfig
import com.example.data.remote.model.InlineData
import com.example.data.remote.model.Part
import com.example.data.remote.model.ThinkingConfig
import com.example.data.remote.model.WebSourceCitation
import com.example.data.service.ImageGenerationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.util.Calendar
import java.util.UUID

class ChatRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) : ChatRepository {

    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val geminiService = RetrofitClient.geminiApiService
    private val imageGenerationService = ImageGenerationService()

    companion object {
        const val PRIMARY_MODEL = "gemini-3.8-flash"

        private const val SYSTEM_PROMPT = """
You are Mayra AI, an intelligent, fast, accurate, and highly capable multimodal AI assistant developed by Soumen Mondal.

Core Instructions:
1. Always reply in the exact language used by the user unless the user requests another language.
2. For simple greetings, arithmetic, definitions, and short factual questions, answer directly and quickly.
3. Never invent current dates, times, news, prices, scores, weather, or other time-sensitive information.
4. When web search results are provided, use them carefully and do not contradict reliable current sources.
5. For image attachments, analyze the image thoroughly including objects, scenery, visible text, charts, diagrams, screenshots, and context.
6. Never attempt to guess or claim the real-world identity of a person in an image.
7. For uploaded files, extract useful information directly from the supplied file content.
8. Format answers cleanly using Markdown where useful.
9. Give complete answers and do not stop unnecessarily before finishing the response.
10. Be concise for simple requests and more detailed when the question requires reasoning.
"""
    }

    override fun getConversations(): Flow<List<ConversationEntity>> =
        conversationDao.getActiveConversations()

    override fun getArchivedConversations(): Flow<List<ConversationEntity>> =
        conversationDao.getArchivedConversations()

    override fun searchConversations(query: String): Flow<List<ConversationEntity>> =
        conversationDao.searchConversations(query)

    override suspend fun getConversationById(id: String): ConversationEntity? =
        withContext(Dispatchers.IO) {
            conversationDao.getConversationById(id)
        }

    override suspend fun createConversation(title: String): ConversationEntity =
        withContext(Dispatchers.IO) {
            val conv = ConversationEntity(
                id = UUID.randomUUID().toString(),
                title = title.ifBlank { "New Conversation" },
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            conversationDao.insertOrUpdate(conv)
            conv
        }

    override suspend fun updateConversationTitle(
        id: String,
        newTitle: String
    ) = withContext(Dispatchers.IO) {
        val existing = conversationDao.getConversationById(id)
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
        val existing = conversationDao.getConversationById(id)
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
        val existing = conversationDao.getConversationById(id)
            ?: return@withContext

        conversationDao.update(
            existing.copy(
                isArchived = isArchived,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteConversation(id: String) =
        withContext(Dispatchers.IO) {
            messageDao.deleteMessagesForConversation(id)
            conversationDao.deleteById(id)
        }

    override fun getMessagesForConversation(
        conversationId: String
    ): Flow<List<MessageEntity>> =
        messageDao.getMessagesForConversation(conversationId)

    override suspend fun deleteMessage(messageId: String) =
        withContext(Dispatchers.IO) {
            val msg = messageDao.getMessageById(messageId)
                ?: return@withContext

            messageDao.delete(msg)
        }

    /**
     * Detect explicit image-generation requests.
     *
     * Important:
     * "তামান্না ভাটিয়ার ছবি দাও" is NOT treated as image generation.
     * Explicit generation words are required.
     */
    private fun isImageGenerationRequest(prompt: String): Boolean {
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
            "draw ",
            "make an image",
            "make a picture",
            "make a photo",
            "paint ",
            "generate a picture of",
            "create a photo of"
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
            "तस्वीर बनाकर",
            "इमेज बनाओ",
            "इमेज बनाना",
            "चित्र बनाओ",
            "फोटो बनाओ",
            "तस्वीर तैयार करो"
        )

        return english.any { clean.startsWith(it) || clean.contains(it) } ||
                bengali.any { clean.contains(it) } ||
                hindi.any { clean.contains(it) }
    }

    /**
     * Detect requests that need current/live web information.
     */
    private fun needsWebSearch(prompt: String): Boolean {
        val clean = prompt.trim().lowercase()

        val keywords = listOf(
            // English
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

            // Bengali
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

            // Hindi
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

        return keywords.any { clean.contains(it) }
    }

    /**
     * Detect simple local date/time questions.
     *
     * These are answered from the device clock instead of asking Gemini,
     * so Mayra does not hallucinate today's date/time.
     */
    private fun localDateTimeAnswer(prompt: String): String? {
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

        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH) + 1
        val year = calendar.get(Calendar.YEAR)

        val hour = calendar.get(Calendar.HOUR)
        val minute = calendar.get(Calendar.MINUTE)
        val amPm = if (calendar.get(Calendar.AM_PM) == Calendar.AM) {
            "AM"
        } else {
            "PM"
        }

        val displayHour = if (hour == 0) 12 else hour

        return when {
            dateRequest && timeRequest ->
                "আজকের তারিখ: %02d/%02d/%04d\nবর্তমান সময়: %02d:%02d %s"
                    .format(day, month, year, displayHour, minute, amPm)

            dateRequest ->
                "আজকের তারিখ: %02d/%02d/%04d"
                    .format(day, month, year)

            else ->
                "বর্তমান সময়: %02d:%02d %s"
                    .format(displayHour, minute, amPm)
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

        val userMessageId = UUID.randomUUID().toString()
        val assistantMessageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // 1. Save user message.
        val userMessage = MessageEntity(
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

        // 2. Update conversation title.
        val conv = conversationDao.getConversationById(conversationId)

        if (conv != null &&
            (conv.title == "New Conversation" || conv.title == "New Chat")
        ) {
            val autoTitle = if (userPrompt.isNotBlank()) {
                userPrompt.take(30).trim() +
                        if (userPrompt.length > 30) "..." else ""
            } else if (!attachmentName.isNullOrBlank()) {
                attachmentName.take(30)
            } else {
                "Conversation"
            }

            conversationDao.update(
                conv.copy(
                    title = autoTitle,
                    updatedAt = now
                )
            )
        } else if (conv != null) {
            conversationDao.update(
                conv.copy(updatedAt = now)
            )
        }

        // 3. Answer simple date/time requests locally.
        val localAnswer = localDateTimeAnswer(userPrompt)

        if (localAnswer != null && attachmentBytes == null) {
            val localMessage = MessageEntity(
                id = assistantMessageId,
                conversationId = conversationId,
                role = "assistant",
                content = localAnswer,
                timestamp = now + 1,
                status = "SENT"
            )

            messageDao.insertOrUpdate(localMessage)

            collector@ this.emit(StreamEvent.TextChunk(localAnswer))
            this.emit(
                StreamEvent.Completed(
                    localAnswer,
                    emptyList()
                )
            )

            return@flow
        }

        // 4. Explicit image generation.
        if (isImageGenerationRequest(userPrompt)) {
            handleImageGeneration(
                conversationId = conversationId,
                assistantMessageId = assistantMessageId,
                userPrompt = userPrompt,
                collector = this
            )

            return@flow
        }

        // 5. Assistant placeholder.
        val assistantMessage = MessageEntity(
            id = assistantMessageId,
            conversationId = conversationId,
            role = "assistant",
            content = "",
            timestamp = now + 1,
            status = "SENDING"
        )

        messageDao.insertOrUpdate(assistantMessage)

        // 6. Previous history.
        val historyMessages = messageDao
            .getMessagesList(conversationId)
            .filter {
                it.id != userMessageId &&
                        it.id != assistantMessageId &&
                        it.status != "ERROR"
            }
            .takeLast(10)

        // 7. Build request.
        val request = buildGeminiRequest(
            history = historyMessages,
            currentPrompt = userPrompt,
            attachmentBytes = attachmentBytes,
            attachmentMimeType = attachmentMimeType,
            attachmentName = attachmentName
        )

        // 8. Streaming response.
        executeStreamCall(
            conversationId = conversationId,
            assistantMessageId = assistantMessageId,
            request = request,
            collector = this
        )
    }.flowOn(Dispatchers.IO)

    override suspend fun retryMessage(
        conversationId: String,
        failedMessageId: String
    ): Flow<StreamEvent> = flow {

        val failedMsg =
            messageDao.getMessageById(failedMessageId)
                ?: return@flow

        val messages =
            messageDao.getMessagesList(conversationId)

        val failedIndex =
            messages.indexOfFirst { it.id == failedMessageId }

        val userMsg =
            if (failedIndex > 0) {
                messages[failedIndex - 1]
            } else {
                null
            }

        val prompt =
            userMsg?.content ?: "Hello"

        // Restore attachment bytes for retry when possible.
        val retryAttachmentBytes =
            if (!userMsg?.attachmentUri.isNullOrBlank()) {
                try {
                    context.contentResolver
                        .openInputStream(
                            Uri.parse(userMsg?.attachmentUri)
                        )
                        ?.use { it.readBytes() }
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }

        messageDao.insertOrUpdate(
            failedMsg.copy(
                content = "",
                status = "SENDING"
            )
        )

        val historyMessages =
            if (failedIndex > 0) {
                messages
                    .take(failedIndex - 1)
                    .filter { it.status != "ERROR" }
                    .takeLast(10)
            } else {
                emptyList()
            }

        val request = buildGeminiRequest(
            history = historyMessages,
            currentPrompt = prompt,
            attachmentBytes = retryAttachmentBytes,
            attachmentMimeType = userMsg?.attachmentMimeType,
            attachmentName = userMsg?.attachmentName
        )

        executeStreamCall(
            conversationId = conversationId,
            assistantMessageId = failedMessageId,
            request = request,
            collector = this
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Generate image using Gemini 3.1 Flash Image.
     *
     * The generated Base64 image is saved to the app's private storage.
     * MessageEntity.imageUrl then points to the local image file.
     *
     * No database migration is required because imageUrl already exists.
     */
    private suspend fun handleImageGeneration(
        conversationId: String,
        assistantMessageId: String,
        userPrompt: String,
        collector: kotlinx.coroutines.flow.FlowCollector<StreamEvent>
    ) {
        val now = System.currentTimeMillis()

        val placeholder = MessageEntity(
            id = assistantMessageId,
            conversationId = conversationId,
            role = "assistant",
            content = "Generating your image...",
            timestamp = now + 1,
            status = "SENDING",
            isGeneratedImage = true
        )

        messageDao.insertOrUpdate(placeholder)

        try {
            val cleanSubject = cleanImagePrompt(userPrompt)

            val result =
                imageGenerationService
                    .generateImage(
                        prompt = cleanSubject,
                        aspectRatio = "1:1",
                        imageSize = "1K"
                    )
                    .getOrThrow()

            val imageUri = saveGeneratedImage(
                messageId = assistantMessageId,
                base64Data = result.base64Data,
                mimeType = result.mimeType
            )

            val textContent =
                result.text?.takeIf { it.isNotBlank() }
                    ?: "Here is the generated image."

            val finalMessage = placeholder.copy(
                content = textContent,
                imageUrl = imageUri,
                status = "SENT",
                isGeneratedImage = true
            )

            messageDao.insertOrUpdate(finalMessage)

            collector.emit(
                StreamEvent.TextChunk(textContent)
            )

            collector.emit(
                StreamEvent.Completed(
                    textContent,
                    emptyList()
                )
            )

        } catch (e: Exception) {

            val errorMsg =
                "Could not generate image: ${
                    e.message ?: "Unknown error"
                }"

            messageDao.insertOrUpdate(
                placeholder.copy(
                    content = errorMsg,
                    status = "ERROR"
                )
            )

            collector.emit(
                StreamEvent.Error(
                    errorMsg,
                    isRetryable = true
                )
            )
        }
    }

    /**
     * Remove common image-generation command words.
     */
    private fun cleanImagePrompt(prompt: String): String {

        var clean = prompt.trim()

        val prefixes = listOf(
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
            clean = clean.replaceFirst(regex, "")
        }

        clean = clean
            .replaceFirst(
                Regex("^ছবি\\s*(তৈরি কর|বানাও|আঁকো)\\s*"),
                ""
            )
            .replaceFirst(
                Regex("^ইমেজ\\s*(তৈরি কর|বানাও)\\s*"),
                ""
            )
            .replaceFirst(
                Regex("^তस्वीर\\s*बनाओ\\s*"),
                ""
            )
            .replaceFirst(
                Regex("^तस्वीर\\s*बनाओ\\s*"),
                ""
            )
            .trim()

        return clean.ifBlank {
            "A beautiful cinematic scene"
        }
    }

    /**
     * Save generated Base64 image to private app storage.
     */
    private suspend fun saveGeneratedImage(
        messageId: String,
        base64Data: String,
        mimeType: String
    ): String = withContext(Dispatchers.IO) {

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
                        mimeType.contains("jpg") -> "jpg"

                mimeType.contains("webp") -> "webp"

                else -> "png"
            }

        val file =
            File(
                directory,
                "$messageId.$extension"
            )

        val imageBytes =
            Base64.decode(
                base64Data,
                Base64.DEFAULT
            )

        file.writeBytes(imageBytes)

        Uri.fromFile(file).toString()
    }

    private fun buildGeminiRequest(
        history: List<MessageEntity>,
        currentPrompt: String,
        attachmentBytes: ByteArray?,
        attachmentMimeType: String?,
        attachmentName: String?
    ): GenerateContentRequest {

        val contents =
            mutableListOf<Content>()

        // History.
        for (msg in history) {

            val role =
                if (msg.role == "user") {
                    "user"
                } else {
                    "model"
                }

            if (msg.content.isNotBlank()) {
                contents.add(
                    Content(
                        role = role,
                        parts = listOf(
                            Part(text = msg.content)
                        )
                    )
                )
            }
        }

        // Current user message.
        val currentParts =
            mutableListOf<Part>()

        var textContent =
            currentPrompt

        if (
            attachmentBytes != null &&
            attachmentMimeType != null
        ) {

            if (attachmentMimeType.startsWith("image/")) {

                val base64Data =
                    Base64.encodeToString(
                        attachmentBytes,
                        Base64.NO_WRAP
                    )

                currentParts.add(
                    Part(
                        inlineData = InlineData(
                            mimeType = attachmentMimeType,
                            data = base64Data
                        )
                    )
                )

            } else if (
                attachmentMimeType == "application/pdf"
            ) {

                val base64Data =
                    Base64.encodeToString(
                        attachmentBytes,
                        Base64.NO_WRAP
                    )

                currentParts.add(
                    Part(
                        inlineData = InlineData(
                            mimeType = "application/pdf",
                            data = base64Data
                        )
                    )
                )

            } else if (
                attachmentMimeType.startsWith("text/") ||
                attachmentMimeType.contains("json") ||
                attachmentMimeType.contains("csv") ||
                attachmentMimeType.contains("markdown")
            ) {

                val fileText =
                    String(
                        attachmentBytes,
                        Charsets.UTF_8
                    )

                val header =
                    if (!attachmentName.isNullOrBlank()) {
                        "--- Attached File: $attachmentName ---\n"
                    } else {
                        ""
                    }

                textContent =
                    "$header$fileText\n\n$currentPrompt"
            }
        }

        if (textContent.isNotBlank()) {

            currentParts.add(
                Part(text = textContent)
            )

        } else if (currentParts.isEmpty()) {

            currentParts.add(
                Part(text = "Hello!")
            )
        }

        contents.add(
            Content(
                role = "user",
                parts = currentParts
            )
        )

        // Current web-search detection.
        val tools: List<JsonObject>? =
            if (needsWebSearch(currentPrompt)) {
                listOf(
                    buildJsonObject {
                        putJsonObject("googleSearch") {}
                    }
                )
            } else {
                null
            }

        return GenerateContentRequest(
            contents = contents,

            generationConfig =
                GenerationConfig(
                    temperature = 0.7f,
                    topP = 0.95f,
                    thinkingConfig =
                        ThinkingConfig(
                            thinkingLevel = "low"
                        )
                ),

            tools = tools,

            systemInstruction =
                Content(
                    parts = listOf(
                        Part(
                            text = SYSTEM_PROMPT
                        )
                    )
                )
        )
    }

    private suspend fun executeStreamCall(
        conversationId: String,
        assistantMessageId: String,
        request: GenerateContentRequest,
        collector: kotlinx.coroutines.flow.FlowCollector<StreamEvent>
    ) {

        val apiKey =
            BuildConfig.GEMINI_API_KEY

        val fullTextBuilder =
            StringBuilder()

        val discoveredSources =
            mutableListOf<WebSourceCitation>()

        val maxAttempts = 3
        var currentAttempt = 0
        var backoffMs = 1000L

        var lastErrorMessage =
            "An unexpected error occurred."

        while (
            currentAttempt < maxAttempts
        ) {

            currentAttempt++

            try {

                if (apiKey.isBlank()) {

                    val demoResponse =
                        "Mayra AI is ready, but the Gemini API key is not configured."

                    messageDao.insertOrUpdate(
                        MessageEntity(
                            id = assistantMessageId,
                            conversationId = conversationId,
                            role = "assistant",
                            content = demoResponse,
                            timestamp = System.currentTimeMillis(),
                            status = "SENT"
                        )
                    )

                    collector.emit(
                        StreamEvent.TextChunk(
                            demoResponse
                        )
                    )

                    collector.emit(
                        StreamEvent.Completed(
                            demoResponse,
                            emptyList()
                        )
                    )

                    return
                }

                val responseBody =
                    geminiService.streamGenerateContent(
                        model = PRIMARY_MODEL,
                        apiKey = apiKey,
                        request = request
                    )

                responseBody
                    .byteStream()
                    .bufferedReader()
                    .use { reader ->

                        var line: String?

                        while (
                            reader.readLine()
                                .also { line = it } != null
                        ) {

                            val rawLine =
                                line?.trim()
                                    ?: continue

                            if (!rawLine.startsWith("data:")) {
                                continue
                            }

                            val jsonPayload =
                                rawLine
                                    .removePrefix("data:")
                                    .trim()

                            if (
                                jsonPayload.isEmpty() ||
                                jsonPayload == "[DONE]"
                            ) {
                                continue
                            }

                            try {

                                val candidate =
                                    RetrofitClient.json
                                        .decodeFromString<CandidateContainer>(
                                            jsonPayload
                                        )

                                val firstCandidate =
                                    candidate
                                        .candidates
                                        ?.firstOrNull()

                                val partText =
                                    firstCandidate
                                        ?.content
                                        ?.parts
                                        ?.firstOrNull()
                                        ?.text

                                if (!partText.isNullOrEmpty()) {

                                    fullTextBuilder.append(
                                        partText
                                    )

                                    collector.emit(
                                        StreamEvent.TextChunk(
                                            partText
                                        )
                                    )

                                    messageDao.insertOrUpdate(
                                        MessageEntity(
                                            id = assistantMessageId,
                                            conversationId = conversationId,
                                            role = "assistant",
                                            content =
                                                fullTextBuilder.toString(),
                                            timestamp =
                                                System.currentTimeMillis(),
                                            status = "SENDING"
                                        )
                                    )
                                }

                                // Grounding sources.
                                val chunks =
                                    firstCandidate
                                        ?.groundingMetadata
                                        ?.groundingChunks

                                if (chunks != null) {

                                    for (chunk in chunks) {

                                        val uri =
                                            chunk.web?.uri

                                        val title =
                                            chunk.web?.title
                                                ?: uri

                                        if (
                                            !uri.isNullOrBlank() &&
                                            !title.isNullOrBlank()
                                        ) {

                                            val citation =
                                                WebSourceCitation(
                                                    title = title,
                                                    url = uri
                                                )

                                            if (
                                                discoveredSources
                                                    .none {
                                                        it.url == uri
                                                    }
                                            ) {
                                                discoveredSources.add(
                                                    citation
                                                )
                                            }
                                        }
                                    }

                                    if (
                                        discoveredSources.isNotEmpty()
                                    ) {
                                        collector.emit(
                                            StreamEvent.SourcesDiscovered(
                                                discoveredSources
                                            )
                                        )
                                    }
                                }

                            } catch (_: Exception) {
                                // Ignore malformed individual SSE chunks.
                            }
                        }
                    }

                val finalText =
                    fullTextBuilder
                        .toString()
                        .ifBlank {
                            "I received your request but didn't generate any text. Please try again."
                        }

                val sourcesJson =
                    if (
                        discoveredSources.isNotEmpty()
                    ) {
                        RetrofitClient.json.encodeToString(
                            discoveredSources
                        )
                    } else {
                        null
                    }

                messageDao.insertOrUpdate(
                    MessageEntity(
                        id = assistantMessageId,
                        conversationId = conversationId,
                        role = "assistant",
                        content = finalText,
                        timestamp = System.currentTimeMillis(),
                        status = "SENT",
                        sourcesJson = sourcesJson
                    )
                )

                collector.emit(
                    StreamEvent.Completed(
                        finalText,
                        discoveredSources
                    )
                )

                return

            } catch (httpEx: HttpException) {

                val code =
                    httpEx.code()

                lastErrorMessage =
                    "API error ($code): ${httpEx.message()}"

                // IMPORTANT:
                // If text has already arrived, do not retry,
                // otherwise the answer can be duplicated.
                if (fullTextBuilder.isNotEmpty()) {
                    break
                }

                if (
                    code == 429 ||
                    code >= 500
                ) {

                    if (
                        currentAttempt < maxAttempts
                    ) {

                        delay(backoffMs)
                        backoffMs *= 2
                        continue
                    }

                } else {
                    break
                }

            } catch (ioEx: IOException) {

                lastErrorMessage =
                    "Network connection failed. Please check your internet connection."

                // Do not restart a partially streamed answer.
                if (fullTextBuilder.isNotEmpty()) {
                    break
                }

                if (
                    currentAttempt < maxAttempts
                ) {

                    delay(backoffMs)
                    backoffMs *= 2
                    continue
                }

            } catch (e: Exception) {

                lastErrorMessage =
                    e.message
                        ?: "An unexpected error occurred."

                break
            }
        }

        val fallbackText =
            fullTextBuilder.toString()

        val errorText =
            if (fallbackText.isNotBlank()) {
                "$fallbackText\n\n⚠️ $lastErrorMessage"
            } else {
                "⚠️ $lastErrorMessage"
            }

        messageDao.insertOrUpdate(
            MessageEntity(
                id = assistantMessageId,
                conversationId = conversationId,
                role = "assistant",
                content = errorText,
                timestamp = System.currentTimeMillis(),
                status = "ERROR"
            )
        )

        collector.emit(
            StreamEvent.Error(
                lastErrorMessage,
                isRetryable = true
            )
        )
    }
}

@kotlinx.serialization.Serializable
private data class CandidateContainer(
    val candidates: List<Candidate>? = null
)
