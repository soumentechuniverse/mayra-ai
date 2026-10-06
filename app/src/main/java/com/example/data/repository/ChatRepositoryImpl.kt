package com.example.data.repository

import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.putJsonObject
import retrofit2.HttpException
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID

class ChatRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) : ChatRepository {

    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val geminiService = RetrofitClient.geminiApiService

    companion object {
        const val PRIMARY_MODEL = "gemini-3.8-flash"

        private const val SYSTEM_PROMPT = """
You are Mayra AI, an intelligent, fast, and highly capable multimodal AI assistant developed by Soumen Mondal.

Core Instructions:
1. Always reply in the exact language used by the user (Bengali, Hindi, English, Urdu, Arabic, Spanish, French, German, etc.) unless requested otherwise.
2. For simple greetings, arithmetic, definitions, and short factual questions, be direct, natural, and answer immediately without unnecessary preamble.
3. For image attachments, analyze the image thoroughly (objects, scenery, text, charts, diagrams, screenshots, context). If the image contains a person, do not attempt to guess or claim to identify their real personal identity.
4. For uploaded files, extract insights directly from the document content provided.
5. Format your output cleanly in Markdown using bold, lists, tables, and fenced code blocks with language identifiers where appropriate.
6. When web search citations are available, seamlessly incorporate the findings into your answer.
"""
    }

    override fun getConversations(): Flow<List<ConversationEntity>> =
        conversationDao.getActiveConversations()

    override fun getArchivedConversations(): Flow<List<ConversationEntity>> =
        conversationDao.getArchivedConversations()

    override fun searchConversations(query: String): Flow<List<ConversationEntity>> =
        conversationDao.searchConversations(query)

    override suspend fun getConversationById(id: String): ConversationEntity? =
        conversationDao.getConversationById(id)

    override suspend fun createConversation(title: String): ConversationEntity {
        val conv = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "New Conversation" },
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        conversationDao.insertOrUpdate(conv)
        return conv
    }

    override suspend fun updateConversationTitle(id: String, newTitle: String) {
        val existing = conversationDao.getConversationById(id) ?: return
        conversationDao.update(existing.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun togglePinConversation(id: String, isPinned: Boolean) {
        val existing = conversationDao.getConversationById(id) ?: return
        conversationDao.update(existing.copy(isPinned = isPinned, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun toggleArchiveConversation(id: String, isArchived: Boolean) {
        val existing = conversationDao.getConversationById(id) ?: return
        conversationDao.update(existing.copy(isArchived = isArchived, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteConversation(id: String) {
        messageDao.deleteMessagesForConversation(id)
        conversationDao.deleteById(id)
    }

    override fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForConversation(conversationId)

    override suspend fun deleteMessage(messageId: String) {
        val msg = messageDao.getMessageById(messageId) ?: return
        messageDao.delete(msg)
    }

    private fun isImageGenerationRequest(prompt: String): Boolean {
        val clean = prompt.trim().lowercase()
        return clean.startsWith("generate image") ||
                clean.startsWith("generate an image") ||
                clean.startsWith("create an image") ||
                clean.startsWith("create image") ||
                clean.startsWith("draw ") ||
                clean.startsWith("make an image of") ||
                clean.startsWith("paint ") ||
                clean.contains("generate a picture of") ||
                clean.contains("create a photo of")
    }

    private fun needsWebSearch(prompt: String): Boolean {
        val clean = prompt.trim().lowercase()
        val keywords = listOf(
            "latest", "today", "yesterday", "news", "current", "weather",
            "stock price", "price of", "who won", "score", "schedule",
            "recent", "upcoming", "live", "search the web", "google for",
            "who is the current", "what happened in"
        )
        return keywords.any { clean.contains(it) }
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

        // 1. Insert User Message
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

        // Update conversation title if first message
        val conv = conversationDao.getConversationById(conversationId)
        if (conv != null && (conv.title == "New Conversation" || conv.title == "New Chat")) {
            val autoTitle = if (userPrompt.isNotBlank()) {
                userPrompt.take(30).trim() + if (userPrompt.length > 30) "..." else ""
            } else if (!attachmentName.isNullOrBlank()) {
                attachmentName.take(30)
            } else {
                "Conversation"
            }
            conversationDao.update(conv.copy(title = autoTitle, updatedAt = now))
        } else if (conv != null) {
            conversationDao.update(conv.copy(updatedAt = now))
        }

        // 2. Check for explicit Image Generation
        if (isImageGenerationRequest(userPrompt)) {
            handleImageGeneration(
                conversationId = conversationId,
                assistantMessageId = assistantMessageId,
                userPrompt = userPrompt,
                collector = this
            )
            return@flow
        }

        // 3. Insert Assistant Placeholder (status: SENDING)
        val assistantMessage = MessageEntity(
            id = assistantMessageId,
            conversationId = conversationId,
            role = "assistant",
            content = "",
            timestamp = now + 1,
            status = "SENDING"
        )
        messageDao.insertOrUpdate(assistantMessage)

        // 4. Fetch Previous History (excluding current message)
        val historyMessages = messageDao.getMessagesList(conversationId)
            .filter { it.id != userMessageId && it.id != assistantMessageId && it.status != "ERROR" }
            .takeLast(10)

        // 5. Build Gemini Request
        val request = buildGeminiRequest(
            history = historyMessages,
            currentPrompt = userPrompt,
            attachmentBytes = attachmentBytes,
            attachmentMimeType = attachmentMimeType,
            attachmentName = attachmentName
        )

        // 6. Execute Streaming Call with Retry for 429/5xx
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
        val failedMsg = messageDao.getMessageById(failedMessageId) ?: return@flow
        val messages = messageDao.getMessagesList(conversationId)
        val failedIndex = messages.indexOfFirst { it.id == failedMessageId }
        val userMsg = if (failedIndex > 0) messages[failedIndex - 1] else null
        val prompt = userMsg?.content ?: "Hello"

        // Mark assistant message as SENDING
        messageDao.insertOrUpdate(failedMsg.copy(content = "", status = "SENDING"))

        val historyMessages = messages.take(failedIndex - 1).filter { it.status != "ERROR" }.takeLast(10)
        val request = buildGeminiRequest(
            history = historyMessages,
            currentPrompt = prompt,
            attachmentBytes = null,
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
            val cleanSubject = userPrompt
                .replace(Regex("(?i)^(generate|create|draw|make|paint)\\s+(an?\\s+)?(image|picture|photo)?(\\s+of)?"), "")
                .trim()
                .ifBlank { "beautiful scenery" }

            val encoded = URLEncoder.encode(cleanSubject, "UTF-8")
            val imageUrl = "https://image.pollinations.ai/prompt/$encoded?width=1024&height=1024&nologo=true"
            val textContent = "Here is the image for: **$cleanSubject**"

            messageDao.insertOrUpdate(
                placeholder.copy(
                    content = textContent,
                    imageUrl = imageUrl,
                    status = "SENT"
                )
            )

            collector.emit(StreamEvent.TextChunk(textContent))
            collector.emit(StreamEvent.Completed(textContent, emptyList()))
        } catch (e: Exception) {
            val errorMsg = "Could not generate image: ${e.message}"
            messageDao.insertOrUpdate(placeholder.copy(content = errorMsg, status = "ERROR"))
            collector.emit(StreamEvent.Error(errorMsg, isRetryable = true))
        }
    }

    private fun buildGeminiRequest(
        history: List<MessageEntity>,
        currentPrompt: String,
        attachmentBytes: ByteArray?,
        attachmentMimeType: String?,
        attachmentName: String?
    ): GenerateContentRequest {
        val contents = mutableListOf<Content>()

        // Add history
        for (msg in history) {
            val role = if (msg.role == "user") "user" else "model"
            if (msg.content.isNotBlank()) {
                contents.add(Content(role = role, parts = listOf(Part(text = msg.content))))
            }
        }

        // Build current user message parts
        val currentParts = mutableListOf<Part>()
        var textContent = currentPrompt

        if (attachmentBytes != null && attachmentMimeType != null) {
            if (attachmentMimeType.startsWith("image/")) {
                val base64Data = Base64.encodeToString(attachmentBytes, Base64.NO_WRAP)
                currentParts.add(Part(inlineData = InlineData(mimeType = attachmentMimeType, data = base64Data)))
            } else if (attachmentMimeType == "application/pdf") {
                val base64Data = Base64.encodeToString(attachmentBytes, Base64.NO_WRAP)
                currentParts.add(Part(inlineData = InlineData(mimeType = "application/pdf", data = base64Data)))
            } else if (attachmentMimeType.startsWith("text/") ||
                attachmentMimeType.contains("json") ||
                attachmentMimeType.contains("csv") ||
                attachmentMimeType.contains("markdown")
            ) {
                val fileText = String(attachmentBytes, Charsets.UTF_8)
                val header = if (!attachmentName.isNullOrBlank()) "--- Attached File: $attachmentName ---\n" else ""
                textContent = "$header$fileText\n\n$currentPrompt"
            }
        }

        if (textContent.isNotBlank()) {
            currentParts.add(Part(text = textContent))
        } else if (currentParts.isEmpty()) {
            currentParts.add(Part(text = "Hello!"))
        }

        contents.add(Content(role = "user", parts = currentParts))

        // Web search tools if query requires current info
        val tools: List<JsonObject>? = if (needsWebSearch(currentPrompt)) {
            listOf(buildJsonObject { putJsonObject("googleSearch") {} })
        } else {
            null
        }

        return GenerateContentRequest(
            contents = contents,
            generationConfig = GenerationConfig(
                temperature = 0.7f,
                topP = 0.95f,
                thinkingConfig = ThinkingConfig(thinkingLevel = "low")
            ),
            tools = tools,
            systemInstruction = Content(parts = listOf(Part(text = SYSTEM_PROMPT)))
        )
    }

    private suspend fun executeStreamCall(
        conversationId: String,
        assistantMessageId: String,
        request: GenerateContentRequest,
        collector: kotlinx.coroutines.flow.FlowCollector<StreamEvent>
    ) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val fullTextBuilder = StringBuilder()
        val discoveredSources = mutableListOf<WebSourceCitation>()

        var maxAttempts = 3
        var currentAttempt = 0
        var backoffMs = 1000L
        var success = false
        var lastErrorMessage = ""

        while (currentAttempt < maxAttempts && !success) {
            currentAttempt++
            try {
                if (apiKey.isBlank()) {
                    // Fallback demo response if no API key is configured yet
                    val demoResponse = "Mayra AI is ready! Please configure your GEMINI_API_KEY in the Secrets panel to enable full real-time intelligence.\n\n" +
                            "I can assist you with code, reasoning, multimodal analysis, and multilingual queries in Bengali, Hindi, English, and more!"
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
                    collector.emit(StreamEvent.TextChunk(demoResponse))
                    collector.emit(StreamEvent.Completed(demoResponse, emptyList()))
                    return
                }

                val responseBody = geminiService.streamGenerateContent(
                    model = PRIMARY_MODEL,
                    apiKey = apiKey,
                    request = request
                )

                responseBody.byteStream().bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val rawLine = line?.trim() ?: continue
                        if (!rawLine.startsWith("data:")) continue
                        val jsonPayload = rawLine.removePrefix("data:").trim()
                        if (jsonPayload.isEmpty() || jsonPayload == "[DONE]") continue

                        try {
                            val candidate = RetrofitClient.json.decodeFromString<CandidateContainer>(jsonPayload)
                            val firstCandidate = candidate.candidates?.firstOrNull()

                            // Extract text part
                            val partText = firstCandidate?.content?.parts?.firstOrNull()?.text
                            if (!partText.isNullOrEmpty()) {
                                fullTextBuilder.append(partText)
                                collector.emit(StreamEvent.TextChunk(partText))

                                // Periodically update partial text in Room
                                messageDao.insertOrUpdate(
                                    MessageEntity(
                                        id = assistantMessageId,
                                        conversationId = conversationId,
                                        role = "assistant",
                                        content = fullTextBuilder.toString(),
                                        timestamp = System.currentTimeMillis(),
                                        status = "SENDING"
                                    )
                                )
                            }

                            // Extract grounding sources
                            val chunks = firstCandidate?.groundingMetadata?.groundingChunks
                            if (chunks != null) {
                                for (chunk in chunks) {
                                    val uri = chunk.web?.uri
                                    val title = chunk.web?.title ?: uri
                                    if (!uri.isNullOrBlank() && !title.isNullOrBlank()) {
                                        val citation = WebSourceCitation(title = title, url = uri)
                                        if (discoveredSources.none { it.url == uri }) {
                                            discoveredSources.add(citation)
                                        }
                                    }
                                }
                                if (discoveredSources.isNotEmpty()) {
                                    collector.emit(StreamEvent.SourcesDiscovered(discoveredSources))
                                }
                            }
                        } catch (parseEx: Exception) {
                            // Non-fatal chunk parsing error, continue reading next stream lines
                        }
                    }
                }

                val finalText = fullTextBuilder.toString().ifBlank {
                    "I received your request but didn't generate any text. Please try again."
                }

                val sourcesJson = if (discoveredSources.isNotEmpty()) {
                    RetrofitClient.json.encodeToString(discoveredSources)
                } else null

                // Final update with status SENT
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

                collector.emit(StreamEvent.Completed(finalText, discoveredSources))
                success = true
                return

            } catch (httpEx: HttpException) {
                val code = httpEx.code()
                lastErrorMessage = "API error ($code): ${httpEx.message()}"
                // Retry only 429 and 5xx
                if (code == 429 || code >= 500) {
                    if (currentAttempt < maxAttempts) {
                        delay(backoffMs)
                        backoffMs *= 2
                        continue
                    }
                } else {
                    // Do not retry 400, 401, 403, 404
                    break
                }
            } catch (ioEx: IOException) {
                lastErrorMessage = "Network connection failed. Please check your internet connection."
                if (currentAttempt < maxAttempts) {
                    delay(backoffMs)
                    backoffMs *= 2
                    continue
                }
            } catch (e: Exception) {
                lastErrorMessage = e.message ?: "An unexpected error occurred."
                break
            }
        }

        // On failure: update Room status to ERROR and emit Error event
        val fallbackText = fullTextBuilder.toString()
        val errorText = if (fallbackText.isNotBlank()) {
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

        collector.emit(StreamEvent.Error(lastErrorMessage, isRetryable = true))
    }
}

@kotlinx.serialization.Serializable
private data class CandidateContainer(
    val candidates: List<Candidate>? = null
)
