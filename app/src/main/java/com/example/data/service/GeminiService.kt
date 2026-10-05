package com.example.data.service

import com.example.BuildConfig
import com.example.domain.model.AiModelConfig
import com.example.domain.model.AiStreamChunk
import com.example.domain.model.Attachment
import com.example.domain.model.AttachmentType
import com.example.domain.model.ChatMessage
import com.example.domain.model.MessageRole
import com.example.domain.model.MessageStatus
import com.example.domain.model.SearchSource
import com.example.domain.service.AiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Gemini AI service for Mayra AI.
 *
 * Supports:
 * - Gemini text generation
 * - Streaming responses
 * - Multilingual responses
 * - Conversation history
 * - Image understanding
 * - PDF understanding
 * - Document understanding
 * - Google Search grounding
 * - Current date awareness
 * - 429 / 503 / 5xx retry
 * - Request cancellation
 */
class GeminiService(
    private val apiKeyProvider: () -> String = {
        BuildConfig.GEMINI_API_KEY
    },
    private val okHttpClient: OkHttpClient = createDefaultClient()
) : AiService {

    companion object {

        private const val BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models"

        private const val DEFAULT_MODEL =
            "gemini-3.8-flash"

        private const val PLACEHOLDER_KEY =
            "MY_GEMINI_API_KEY"

        private const val MAX_TRANSIENT_RETRIES =
            2

        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8".toMediaType()

        private fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    // ------------------------------------------------------------------------
    // API AVAILABILITY
    // ------------------------------------------------------------------------

    override suspend fun isAvailable(): Boolean {
        val key = apiKeyProvider()
            .trim()
            .removeSurrounding("\"")

        return key.isNotEmpty() &&
                key != PLACEHOLDER_KEY &&
                key.length > 10
    }

    // ------------------------------------------------------------------------
    // NON-STREAMING RESPONSE
    // ------------------------------------------------------------------------

    override suspend fun generateResponse(
        conversationId: String,
        prompt: String,
        history: List<ChatMessage>,
        config: AiModelConfig,
        attachments: List<Attachment>,
        enableSearch: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {

        try {

            val result = StringBuilder()

            generateStream(
                conversationId = conversationId,
                prompt = prompt,
                history = history,
                config = config,
                attachments = attachments,
                enableSearch = enableSearch
            ).collect { chunk ->

                if (!chunk.isComplete) {
                    result.append(chunk.textDelta)
                }
            }

            val finalText =
                result.toString().trim()

            if (finalText.isEmpty()) {

                Result.failure(
                    IllegalStateException(
                        "Gemini returned an empty response."
                    )
                )

            } else {

                Result.success(finalText)
            }

        } catch (e: CancellationException) {

            throw e

        } catch (e: Exception) {

            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------
    // STREAMING RESPONSE
    // ------------------------------------------------------------------------

    override fun generateStream(
        conversationId: String,
        prompt: String,
        history: List<ChatMessage>,
        config: AiModelConfig,
        attachments: List<Attachment>,
        enableSearch: Boolean
    ): Flow<AiStreamChunk> = flow {

        val apiKey =
            apiKeyProvider()
                .trim()
                .removeSurrounding("\"")

        // --------------------------------------------------------------------
        // API KEY
        // --------------------------------------------------------------------

        if (
            apiKey.isEmpty() ||
            apiKey == PLACEHOLDER_KEY ||
            apiKey.length <= 10
        ) {
            throw IllegalStateException(
                "Gemini API key is not configured."
            )
        }

        // --------------------------------------------------------------------
        // PROMPT
        // --------------------------------------------------------------------

        val trimmedPrompt =
            prompt.trim()

        val effectivePrompt =
            if (
                trimmedPrompt.isEmpty() &&
                attachments.isNotEmpty()
            ) {
                resolveDefaultPrompt(attachments)
            } else {
                trimmedPrompt
            }

        if (effectivePrompt.isEmpty()) {

            throw IllegalArgumentException(
                "Message content or attachment cannot be empty."
            )
        }

        // --------------------------------------------------------------------
        // SEARCH STATUS
        // --------------------------------------------------------------------

        if (enableSearch) {

            emit(
                AiStreamChunk(
                    conversationId = conversationId,
                    textDelta = "",
                    isSearching = true
                )
            )
        }

        // --------------------------------------------------------------------
        // MODEL
        //
        // IMPORTANT:
        // Never use the image model as a text fallback.
        // --------------------------------------------------------------------

        val modelId =
            if (config.modelId.isBlank()) {
                DEFAULT_MODEL
            } else {
                config.modelId
            }

        var attempt = 0
        var lastException: Exception? = null

        // --------------------------------------------------------------------
        // RETRY LOOP
        // --------------------------------------------------------------------

        while (attempt <= MAX_TRANSIENT_RETRIES) {

            currentCoroutineContext().ensureActive()

            val requestPayload =
                buildRequestJson(
                    prompt = effectivePrompt,
                    history = history,
                    config = config.copy(
                        modelId = modelId
                    ),
                    attachments = attachments,
                    enableSearch = enableSearch
                )

            val endpoint =
                "$BASE_URL/$modelId:streamGenerateContent" +
                        "?alt=sse&key=$apiKey"

            val request =
                Request.Builder()
                    .url(endpoint)
                    .post(
                        requestPayload
                            .toString()
                            .toRequestBody(JSON_MEDIA_TYPE)
                    )
                    .header(
                        "Accept",
                        "text/event-stream"
                    )
                    .header(
                        "Cache-Control",
                        "no-cache"
                    )
                    .build()

            val call =
                okHttpClient.newCall(request)

            try {

                call.execute().use { response ->

                    // --------------------------------------------------------
                    // HTTP ERROR
                    // --------------------------------------------------------

                    if (!response.isSuccessful) {

                        handleHttpError(
                            response = response,
                            apiKey = apiKey,
                            modelId = modelId
                        )
                    }

                    val body =
                        response.body
                            ?: throw IllegalStateException(
                                "Gemini returned an empty response body."
                            )

                    val accumulatedSources =
                        mutableListOf<SearchSource>()

                    val seenUrls =
                        mutableSetOf<String>()

                    var hasEmittedText = false

                    val reader =
                        body
                            .byteStream()
                            .bufferedReader(Charsets.UTF_8)

                    // --------------------------------------------------------
                    // SSE STREAM
                    // --------------------------------------------------------

                    reader.use { bufferedReader ->

                        var line: String?

                        while (
                            bufferedReader
                                .readLine()
                                .also {
                                    line = it
                                } != null
                        ) {

                            currentCoroutineContext()
                                .ensureActive()

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

                            // ------------------------------------------------
                            // GOOGLE SEARCH GROUNDING
                            // ------------------------------------------------

                            val grounding =
                                parseGroundingMetadata(
                                    jsonPayload
                                )

                            if (grounding != null) {

                                val queries =
                                    grounding.first

                                val sources =
                                    grounding.second

                                val newSources =
                                    mutableListOf<SearchSource>()

                                for (source in sources) {

                                    if (
                                        seenUrls.add(
                                            source.url
                                        )
                                    ) {

                                        accumulatedSources.add(
                                            source
                                        )

                                        newSources.add(
                                            source
                                        )
                                    }
                                }

                                if (
                                    queries.isNotEmpty() ||
                                    newSources.isNotEmpty()
                                ) {

                                    emit(
                                        AiStreamChunk(
                                            conversationId =
                                                conversationId,
                                            textDelta = "",
                                            searchQueries =
                                                queries,
                                            searchSources =
                                                accumulatedSources
                                                    .toList(),
                                            isSearching = false
                                        )
                                    )
                                }
                            }

                            // ------------------------------------------------
                            // TEXT DELTA
                            // ------------------------------------------------

                            val delta =
                                parseTextDelta(
                                    jsonPayload
                                )

                            if (!delta.isNullOrEmpty()) {

                                hasEmittedText = true

                                emit(
                                    AiStreamChunk(
                                        conversationId =
                                            conversationId,
                                        textDelta = delta,
                                        isComplete = false,
                                        searchSources =
                                            accumulatedSources
                                                .toList()
                                    )
                                )
                            }
                        }
                    }

                    // --------------------------------------------------------
                    // EMPTY RESPONSE
                    // --------------------------------------------------------

                    if (!hasEmittedText) {

                        throw IllegalStateException(
                            "No text content received from Gemini."
                        )
                    }

                    // --------------------------------------------------------
                    // COMPLETE
                    // --------------------------------------------------------

                    emit(
                        AiStreamChunk(
                            conversationId =
                                conversationId,
                            textDelta = "",
                            isComplete = true,
                            searchSources =
                                accumulatedSources.toList()
                        )
                    )

                    return@flow
                }

            } catch (e: CancellationException) {

                call.cancel()

                throw e

            } catch (e: Exception) {

                call.cancel()

                lastException = e

                // ------------------------------------------------------------
                // DO NOT RETRY AFTER PARTIAL RESPONSE
                // ------------------------------------------------------------

                if (lastException is GeminiApiException) {
                    if (
                        lastException.statusCode == 400 ||
                        lastException.statusCode == 401 ||
                        lastException.statusCode == 403 ||
                        lastException.statusCode == 404
                    ) {
                        throw lastException
                    }
                }

                // ------------------------------------------------------------
                // TRANSIENT ERROR
                // ------------------------------------------------------------

                if (
                    isTransientError(e) &&
                    attempt < MAX_TRANSIENT_RETRIES
                ) {

                    attempt++

                    val retryAfter =
                        (e as? GeminiApiException)
                            ?.retryAfterSeconds

                    delay(
                        calculateRetryDelay(
                            attempt = attempt,
                            retryAfterSeconds = retryAfter
                        )
                    )

                } else {

                    throw e
                }
            }
        }

        throw (
            lastException
                ?: IllegalStateException(
                    "Mayra AI service is currently unavailable. Please try again."
                )
            )

    }.flowOn(Dispatchers.IO)

    // ------------------------------------------------------------------------
    // RETRY DELAY
    // ------------------------------------------------------------------------

    private fun calculateRetryDelay(
        attempt: Int,
        retryAfterSeconds: Long?
    ): Long {

        if (
            retryAfterSeconds != null &&
            retryAfterSeconds in 1..60
        ) {

            return retryAfterSeconds * 1000L
        }

        val base =
            when (attempt) {
                1 -> 1200L
                2 -> 2500L
                else -> 4000L
            }

        val jitter =
            Random.nextLong(
                from = 100L,
                until = 400L
            )

        return base + jitter
    }

    // ------------------------------------------------------------------------
    // DEFAULT ATTACHMENT PROMPT
    // ------------------------------------------------------------------------

    private fun resolveDefaultPrompt(
        attachments: List<Attachment>
    ): String {

        val allImages =
            attachments.isNotEmpty() &&
                    attachments.all {
                        it.type == AttachmentType.IMAGE
                    }

        val allPdfs =
            attachments.isNotEmpty() &&
                    attachments.all {
                        it.type == AttachmentType.PDF
                    }

        val allDocuments =
            attachments.isNotEmpty() &&
                    attachments.all {
                        it.type == AttachmentType.DOCUMENT
                    }

        return when {

            allImages -> {

                if (attachments.size == 1) {

                    "Analyze this image and describe what you can understand from it."

                } else {

                    "Analyze these images and describe the key details."
                }
            }

            allPdfs -> {

                if (attachments.size == 1) {

                    "Analyze this PDF document and summarize its key information."

                } else {

                    "Analyze these PDF documents and summarize their key information."
                }
            }

            allDocuments -> {

                if (attachments.size == 1) {

                    "Analyze this document and explain its key information."

                } else {

                    "Analyze these documents and explain their key information."
                }
            }

            else -> {

                "Analyze the attached files and explain the key information."
            }
        }
    }

    // ------------------------------------------------------------------------
    // BUILD GEMINI REQUEST
    // ------------------------------------------------------------------------

    private fun buildRequestJson(
        prompt: String,
        history: List<ChatMessage>,
        config: AiModelConfig,
        attachments: List<Attachment> = emptyList(),
        enableSearch: Boolean = false
    ): JSONObject {

        val root =
            JSONObject()

        // --------------------------------------------------------------------
        // SYSTEM INSTRUCTION
        // --------------------------------------------------------------------

        val systemInstruction =
            JSONObject()

        val systemParts =
            JSONArray()

        val currentDate =
            LocalDate.now()

        val formattedDate =
            currentDate.format(
                DateTimeFormatter.ofPattern(
                    "dd MMMM yyyy",
                    Locale.ENGLISH
                )
            )

        val enhancedSystemPrompt =
            buildString {

                append(config.systemPrompt)

                append("\n\n")

                append("CURRENT DATE CONTEXT:\n")

                append(
                    "Today is $formattedDate according to the device date.\n"
                )

                append(
                    "Never invent or guess today's date.\n"
                )

                append(
                    "If the user asks for current, latest, today, " +
                            "recent news, current prices, weather, " +
                            "or other time-sensitive information, " +
                            "use Google Search when search is enabled.\n"
                )

                append(
                    "Answer in the same language as the user's latest message " +
                            "unless the user explicitly requests another language.\n"
                )

                append(
                    "Be accurate, direct, and concise. " +
                            "Do not create a fake answer when information is unavailable."
                )
            }

        systemParts.put(
            JSONObject().put(
                "text",
                enhancedSystemPrompt
            )
        )

        systemInstruction.put(
            "parts",
            systemParts
        )

        root.put(
            "systemInstruction",
            systemInstruction
        )

        // --------------------------------------------------------------------
        // GENERATION CONFIG
        // --------------------------------------------------------------------

        val generationConfig =
            JSONObject()

        generationConfig.put(
            "temperature",
            config.temperature
        )

        generationConfig.put(
            "maxOutputTokens",
            config.maxTokens
        )

        root.put(
            "generationConfig",
            generationConfig
        )

        // --------------------------------------------------------------------
        // GOOGLE SEARCH
        // --------------------------------------------------------------------

        if (enableSearch) {

            val tools =
                JSONArray()

            tools.put(
                JSONObject().put(
                    "google_search",
                    JSONObject()
                )
            )

            root.put(
                "tools",
                tools
            )
        }

        // --------------------------------------------------------------------
        // CONTENTS
        // --------------------------------------------------------------------

        val contents =
            JSONArray()

        // --------------------------------------------------------------------
        // HISTORY
        // --------------------------------------------------------------------

        val historyTurns =
            mutableListOf<Pair<String, JSONArray>>()

        for (message in history) {

            // Never send failed messages back to Gemini.
            if (
                message.status ==
                MessageStatus.ERROR
            ) {
                continue
            }

            val rawText =
                message.content.trim()

            if (
                rawText.isEmpty() &&
                message.attachments.isEmpty()
            ) {
                continue
            }

            val role =
                when (message.role) {

                    MessageRole.USER ->
                        "user"

                    MessageRole.ASSISTANT ->
                        "model"

                    MessageRole.SYSTEM ->
                        continue
                }

            val parts =
                JSONArray()

            val displayText =
                if (message.attachments.isNotEmpty()) {

                    val names =
                        message.attachments.joinToString(
                            separator = ", "
                        ) {
                            it.name
                        }

                    if (rawText.isEmpty()) {

                        "[Attached: $names]"

                    } else {

                        "[Attached: $names]\n$rawText"
                    }

                } else {

                    rawText
                }

            parts.put(
                JSONObject().put(
                    "text",
                    displayText
                )
            )

            historyTurns.add(
                Pair(
                    role,
                    parts
                )
            )
        }

        // Gemini history must start with user.
        while (
            historyTurns.isNotEmpty() &&
            historyTurns.first().first != "user"
        ) {
            historyTurns.removeAt(0)
        }

        // Add history.
        for ((role, parts) in historyTurns) {

            val turn =
                JSONObject()

            turn.put(
                "role",
                role
            )

            turn.put(
                "parts",
                parts
            )

            contents.put(
                turn
            )
        }

        // --------------------------------------------------------------------
        // CURRENT USER TURN
        // --------------------------------------------------------------------

        val currentTurn =
            JSONObject()

        currentTurn.put(
            "role",
            "user"
        )

        val currentParts =
            JSONArray()

        // --------------------------------------------------------------------
        // ATTACHMENTS
        // --------------------------------------------------------------------

        for (attachment in attachments) {

            when (attachment.type) {

                // ------------------------------------------------------------
                // IMAGE
                // ------------------------------------------------------------

                AttachmentType.IMAGE -> {

                    val base64 =
                        attachment.base64Data

                    if (!base64.isNullOrBlank()) {

                        val inlineData =
                            JSONObject()

                        inlineData.put(
                            "mimeType",
                            attachment.mimeType
                        )

                        inlineData.put(
                            "data",
                            base64
                        )

                        currentParts.put(
                            JSONObject().put(
                                "inlineData",
                                inlineData
                            )
                        )
                    }
                }

                // ------------------------------------------------------------
                // PDF
                // ------------------------------------------------------------

                AttachmentType.PDF -> {

                    val base64 =
                        attachment.base64Data

                    if (!base64.isNullOrBlank()) {

                        val inlineData =
                            JSONObject()

                        inlineData.put(
                            "mimeType",
                            "application/pdf"
                        )

                        inlineData.put(
                            "data",
                            base64
                        )

                        currentParts.put(
                            JSONObject().put(
                                "inlineData",
                                inlineData
                            )
                        )

                    } else {

                        val text =
                            attachment.textContent

                        if (!text.isNullOrBlank()) {

                            currentParts.put(
                                JSONObject().put(
                                    "text",
                                    "[PDF: ${attachment.name}]\n$text"
                                )
                            )
                        }
                    }
                }

                // ------------------------------------------------------------
                // DOCUMENT
                // ------------------------------------------------------------

                AttachmentType.DOCUMENT -> {

                    val text =
                        attachment.textContent
                            ?: decodeDocumentBase64(
                                attachment.base64Data
                            )

                    if (!text.isNullOrBlank()) {

                        currentParts.put(
                            JSONObject().put(
                                "text",
                                "[Document: ${attachment.name}]\n$text"
                            )
                        )
                    }
                }
            }
        }

        // --------------------------------------------------------------------
        // CURRENT PROMPT
        // --------------------------------------------------------------------

        val textToInclude =
            if (prompt.trim().isNotEmpty()) {

                prompt.trim()

            } else if (attachments.isNotEmpty()) {

                resolveDefaultPrompt(
                    attachments
                )

            } else {

                throw IllegalArgumentException(
                    "Message content cannot be empty."
                )
            }

        currentParts.put(
            JSONObject().put(
                "text",
                textToInclude
            )
        )

        currentTurn.put(
            "parts",
            currentParts
        )

        contents.put(
            currentTurn
        )

        root.put(
            "contents",
            contents
        )

        return root
    }

    // ------------------------------------------------------------------------
    // DOCUMENT BASE64 DECODER
    // ------------------------------------------------------------------------

    private fun decodeDocumentBase64(
        base64: String?
    ): String? {

        if (base64.isNullOrBlank()) {
            return null
        }

        return try {

            String(
                android.util.Base64.decode(
                    base64,
                    android.util.Base64.DEFAULT
                ),
                Charsets.UTF_8
            )

        } catch (
            _: Exception
        ) {

            null
        }
    }

    // ------------------------------------------------------------------------
    // PARSE TEXT DELTA
    // ------------------------------------------------------------------------

    private fun parseTextDelta(
        jsonStr: String
    ): String? {

        return try {

            val root =
                JSONObject(jsonStr)

            val candidates =
                root.optJSONArray(
                    "candidates"
                )
                    ?: return null

            val candidate =
                candidates.optJSONObject(0)
                    ?: return null

            val content =
                candidate.optJSONObject(
                    "content"
                )
                    ?: return null

            val parts =
                content.optJSONArray(
                    "parts"
                )
                    ?: return null

            val result =
                StringBuilder()

            for (
                index in 0 until parts.length()
            ) {

                val part =
                    parts.optJSONObject(index)
                        ?: continue

                val text =
                    part.optString(
                        "text",
                        ""
                    )

                if (text.isNotEmpty()) {
                    result.append(text)
                }
            }

            result.toString()
                .takeIf {
                    it.isNotEmpty()
                }

        } catch (
            _: Exception
        ) {

            null
        }
    }

    // ------------------------------------------------------------------------
    // PARSE GOOGLE SEARCH GROUNDING
    // ------------------------------------------------------------------------

    private fun parseGroundingMetadata(
        jsonStr: String
    ): Pair<List<String>, List<SearchSource>>? {

        return try {

            val root =
                JSONObject(jsonStr)

            val candidates =
                root.optJSONArray(
                    "candidates"
                )
                    ?: return null

            val candidate =
                candidates.optJSONObject(0)
                    ?: return null

            val metadata =
                candidate.optJSONObject(
                    "groundingMetadata"
                )
                    ?: return null

            // ----------------------------------------------------------------
            // SEARCH QUERIES
            // ----------------------------------------------------------------

            val queries =
                mutableListOf<String>()

            val queryArray =
                metadata.optJSONArray(
                    "webSearchQueries"
                )

            if (queryArray != null) {

                for (
                    i in 0 until queryArray.length()
                ) {

                    val query =
                        queryArray.optString(i)

                    if (query.isNotBlank()) {
                        queries.add(query)
                    }
                }
            }

            // ----------------------------------------------------------------
            // SOURCES
            // ----------------------------------------------------------------

            val sources =
                mutableListOf<SearchSource>()

            val chunks =
                metadata.optJSONArray(
                    "groundingChunks"
                )

            if (chunks != null) {

                for (
                    i in 0 until chunks.length()
                ) {

                    val chunk =
                        chunks.optJSONObject(i)
                            ?: continue

                    val web =
                        chunk.optJSONObject(
                            "web"
                        )
                            ?: continue

                    val uri =
                        web.optString(
                            "uri"
                        )

                    val title =
                        web.optString(
                            "title"
                        )

                    if (uri.isNotBlank()) {

                        sources.add(
                            SearchSource(
                                title =
                                    if (title.isNotBlank()) {
                                        title
                                    } else {
                                        SearchSource
                                            .extractDomain(uri)
                                    },
                                url = uri
                            )
                        )
                    }
                }
            }

            Pair(
                queries,
                sources
            )

        } catch (
            _: Exception
        ) {

            null
        }
    }

    // ------------------------------------------------------------------------
    // HTTP ERROR HANDLER
    // ------------------------------------------------------------------------

    private fun handleHttpError(
        response: Response,
        apiKey: String,
        modelId: String
    ): Nothing {

        val code =
            response.code

        val rawBody =
            try {
                response.body?.string()
            } catch (
                _: Exception
            ) {
                null
            }

        val retryAfter =
            response
                .header("Retry-After")
                ?.toLongOrNull()

        val apiMessage =
            extractErrorFromResponse(
                rawBody
            )
                ?.replace(
                    apiKey,
                    "[REDACTED]"
                )

        val userMessage =
            when (code) {

                400 ->

                    "Request error (400): " +
                            (
                                    apiMessage
                                        ?: "Invalid request or model configuration."
                                    )

                401, 403 ->

                    "Gemini authentication failed. " +
                            "Please check the API key configuration."

                404 ->

                    "The Gemini model '$modelId' is unavailable."

                429 ->

                    "Mayra AI is temporarily busy. Please try again shortly."

                503 ->

                    "Mayra AI is temporarily unavailable. Please try again shortly."

                in 500..599 ->

                    "Mayra AI service is temporarily busy. Please try again shortly."

                else ->

                    "Gemini service error ($code). " +
                            (
                                    apiMessage
                                        ?: "Please try again."
                                    )
            }

        throw GeminiApiException(
            statusCode = code,
            message = userMessage,
            retryAfterSeconds = retryAfter
        )
    }

    // ------------------------------------------------------------------------
    // EXTRACT API ERROR
    // ------------------------------------------------------------------------

    private fun extractErrorFromResponse(
        rawBody: String?
    ): String? {

        if (rawBody.isNullOrBlank()) {
            return null
        }

        return try {

            val root =
                JSONObject(rawBody)

            root
                .optJSONObject("error")
                ?.optString("message")
                ?.takeIf {
                    it.isNotBlank()
                }

        } catch (
            _: Exception
        ) {

            null
        }
    }

    // ------------------------------------------------------------------------
    // TRANSIENT ERROR
    // ------------------------------------------------------------------------

    private fun isTransientError(
        throwable: Throwable
    ): Boolean {

        if (throwable is IOException) {
            return true
        }

        if (throwable is GeminiApiException) {

            return throwable.statusCode == 429 ||
                    throwable.statusCode == 503 ||
                    throwable.statusCode in 500..599
        }

        return false
    }
}

// ============================================================================
// GEMINI API EXCEPTION
// ============================================================================

class GeminiApiException(
    val statusCode: Int,
    message: String,
    val retryAfterSeconds: Long? = null
) : Exception(message)
