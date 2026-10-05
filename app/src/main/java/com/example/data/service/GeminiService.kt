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
 * Production-ready Gemini implementation for Mayra AI.
 *
 * Features:
 * - Real-time SSE streaming
 * - Multilingual responses
 * - Current date awareness
 * - Google Search grounding
 * - Image understanding
 * - PDF understanding
 * - Text/document attachments
 * - Conversation history
 * - 429 / 503 / 5xx retry handling
 * - Retry-After support
 * - Safe model fallback
 * - Request cancellation
 * - API key protection
 * - No artificial question limit
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

        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8".toMediaType()

        /*
         * Keep retries limited.
         *
         * Attempt 1 = initial request
         * Retry 1 = short delay
         * Retry 2 = slightly longer delay
         *
         * This prevents very long waiting times.
         */
        private const val MAX_TRANSIENT_RETRIES = 2

        private const val PLACEHOLDER_KEY = "MY_GEMINI_API_KEY"

        private fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
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

            val finalText = result.toString().trim()

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

        val apiKey = apiKeyProvider()
            .trim()
            .removeSurrounding("\"")

        // ------------------------------------------------------------
        // API KEY CHECK
        // ------------------------------------------------------------

        if (
            apiKey.isEmpty() ||
            apiKey == PLACEHOLDER_KEY ||
            apiKey.length <= 10
        ) {
            throw IllegalStateException(
                "Gemini API key is not configured."
            )
        }

        // ------------------------------------------------------------
        // PROMPT
        // ------------------------------------------------------------

        val trimmedPrompt = prompt.trim()

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

        // ------------------------------------------------------------
        // SEARCH STATUS
        // ------------------------------------------------------------

        if (enableSearch) {

            emit(
                AiStreamChunk(
                    conversationId = conversationId,
                    textDelta = "",
                    isSearching = true
                )
            )
        }

        // ------------------------------------------------------------
        // MODEL CANDIDATES
        // ------------------------------------------------------------

        val candidateConfigs = buildList {

            add(config)

            for (available in AiModelConfig.AvailableModels) {

                if (
                    available.modelId != config.modelId &&
                    none {
                        it.modelId == available.modelId
                    }
                ) {

                    add(
                        config.copy(
                            modelId = available.modelId,
                            displayName = available.displayName
                        )
                    )
                }
            }
        }

        var hasEmittedText = false
        var lastException: Exception? = null

        // ------------------------------------------------------------
        // TRY MODELS
        // ------------------------------------------------------------

        for (currentConfig in candidateConfigs) {

            /*
             * Once text has already reached the user,
             * never restart with another model.
             */
            if (hasEmittedText) {
                break
            }

            val isPrimary =
                currentConfig.modelId == config.modelId

            /*
             * Primary model gets limited retries.
             * Fallback models get one retry.
             */
            val maxRetries =
                if (isPrimary) {
                    MAX_TRANSIENT_RETRIES
                } else {
                    1
                }

            var attempt = 0

            // --------------------------------------------------------
            // BUILD REQUEST JSON
            // --------------------------------------------------------

            val requestPayload = buildRequestJson(
                prompt = effectivePrompt,
                history = history,
                config = currentConfig,
                attachments = attachments,
                enableSearch = enableSearch
            )

            val endpoint =
                "$BASE_URL/${currentConfig.modelId}" +
                        ":streamGenerateContent" +
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

            // --------------------------------------------------------
            // RETRY LOOP
            // --------------------------------------------------------

            while (attempt <= maxRetries) {

                currentCoroutineContext().ensureActive()

                val call =
                    okHttpClient.newCall(request)

                try {

                    call.execute().use { response ->

                        // ------------------------------------------------
                        // HTTP ERROR
                        // ------------------------------------------------

                        if (!response.isSuccessful) {
                            handleHttpError(
                                response = response,
                                apiKey = apiKey,
                                modelId = currentConfig.modelId
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

                        val reader =
                            body
                                .byteStream()
                                .bufferedReader(Charsets.UTF_8)

                        // ------------------------------------------------
                        // SSE STREAM
                        // ------------------------------------------------

                        reader.use {

                            var line: String?

                            while (
                                it.readLine()
                                    .also { currentLine ->
                                        line = currentLine
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
                                // SEARCH / GROUNDING
                                // ------------------------------------------------

                                val grounding =
                                    parseGroundingMetadata(
                                        jsonPayload
                                    )

                                if (grounding != null) {

                                    val (
                                        queries,
                                        sources
                                    ) = grounding

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

                        // ------------------------------------------------
                        // EMPTY RESPONSE
                        // ------------------------------------------------

                        if (!hasEmittedText) {

                            throw IllegalStateException(
                                "No text content received from Gemini."
                            )
                        }

                        // ------------------------------------------------
                        // COMPLETE
                        // ------------------------------------------------

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

                    /*
                     * Never retry after partial output.
                     */
                    if (hasEmittedText) {
                        throw e
                    }

                    val transient =
                        isTransientError(e)

                    val modelUnavailable =
                        isModelUnavailableError(e)

                    // ----------------------------------------------------
                    // RETRY
                    // ----------------------------------------------------

                    if (
                        transient &&
                        attempt < maxRetries
                    ) {

                        attempt++

                        val retryAfter =
                            (e as? GeminiApiException)
                                ?.retryAfterSeconds

                        val delayMs =
                            calculateRetryDelay(
                                attempt = attempt,
                                retryAfterSeconds =
                                    retryAfter
                            )

                        delay(delayMs)

                    } else if (modelUnavailable) {

                        /*
                         * Try next available model.
                         */
                        break

                    } else {

                        /*
                         * Permanent error.
                         */
                        throw e
                    }
                }
            }
        }

        // ------------------------------------------------------------
        // EVERYTHING FAILED
        // ------------------------------------------------------------

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
    // REQUEST JSON
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

        // ------------------------------------------------------------
        // SYSTEM INSTRUCTION
        // ------------------------------------------------------------

        val systemInstruction =
            JSONObject()

        val systemParts =
            JSONArray()

        /*
         * Current device date is added dynamically.
         *
         * This prevents the model from relying on an old
         * hard-coded date.
         */
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

                append(
                    "CURRENT DATE CONTEXT:\n"
                )

                append(
                    "Today is $formattedDate according to the device date.\n"
                )

                append(
                    "Never invent or guess today's date. "
                )

                append(
                    "If the user asks for current/latest/today's information "
                )

                append(
                    "and web search is available, verify it before answering.\n"
                )

                append(
                    "If the user asks a normal non-time-sensitive question, "
                )

                append(
                    "answer directly without unnecessary web searching."
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

        // ------------------------------------------------------------
        // GENERATION CONFIG
        // ------------------------------------------------------------

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

        // ------------------------------------------------------------
        // GOOGLE SEARCH
        // ------------------------------------------------------------

        if (enableSearch) {

            val tools =
                JSONArray()

            val googleSearch =
                JSONObject().put(
                    "google_search",
                    JSONObject()
                )

            tools.put(
                googleSearch
            )

            root.put(
                "tools",
                tools
            )
        }

        // ------------------------------------------------------------
        // CONTENTS
        // ------------------------------------------------------------

        val contents =
            JSONArray()

        // ------------------------------------------------------------
        // HISTORY
        // ------------------------------------------------------------

        val historyTurns =
            mutableListOf<
                Pair<String, List<JSONObject>>
                >()

        for (message in history) {

            // Don't send failed messages back to Gemini.
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

            val part =
                JSONObject().put(
                    "text",
                    displayText
                )

            historyTurns.add(
                Pair(
                    role,
                    listOf(part)
                )
            )
        }

        // ------------------------------------------------------------
        // GEMINI HISTORY MUST START WITH USER
        // ------------------------------------------------------------

        while (
            historyTurns.isNotEmpty() &&
            historyTurns.first().first != "user"
        ) {
            historyTurns.removeAt(0)
        }

        // ------------------------------------------------------------
        // ADD HISTORY
        // ------------------------------------------------------------

        for (
            (role, parts) in historyTurns
        ) {

            val turn =
                JSONObject()

            turn.put(
                "role",
                role
            )

            val partsArray =
                JSONArray()

            for (part in parts) {
                partsArray.put(part)
            }

            turn.put(
                "parts",
                partsArray
            )

            contents.put(
                turn
            )
        }

        // ------------------------------------------------------------
        // CURRENT USER TURN
        // ------------------------------------------------------------

        val currentTurn =
            JSONObject()

        currentTurn.put(
            "role",
            "user"
        )

        val currentParts =
            JSONArray()

        // ------------------------------------------------------------
        // ATTACHMENTS
        // ------------------------------------------------------------

        for (attachment in attachments) {

            when (attachment.type) {

                // ----------------------------------------------------
                // IMAGE
                // ----------------------------------------------------

                AttachmentType.IMAGE -> {

                    if (
                        !attachment.base64Data
                            .isNullOrBlank()
                    ) {

                        val inlineData =
                            JSONObject()

                        inlineData.put(
                            "mimeType",
                            attachment.mimeType
                        )

                        inlineData.put(
                            "data",
                            attachment.base64Data
                        )

                        currentParts.put(
                            JSONObject().put(
                                "inlineData",
                                inlineData
                            )
                        )
                    }
                }

                // ----------------------------------------------------
                // PDF
                // ----------------------------------------------------

                AttachmentType.PDF -> {

                    if (
                        !attachment.base64Data
                            .isNullOrBlank()
                    ) {

                        val inlineData =
                            JSONObject()

                        inlineData.put(
                            "mimeType",
                            "application/pdf"
                        )

                        inlineData.put(
                            "data",
                            attachment.base64Data
                        )

                        currentParts.put(
                            JSONObject().put(
                                "inlineData",
                                inlineData
                            )
                        )

                    } else if (
                        !attachment.textContent
                            .isNullOrBlank()
                    ) {

                        currentParts.put(
                            JSONObject().put(
                                "text",
                                "[Document: ${attachment.name}]\n" +
                                        attachment.textContent
                            )
                        )
                    }
                }

                // ----------------------------------------------------
                // DOCUMENT
                // ----------------------------------------------------

                AttachmentType.DOCUMENT -> {

                    val content =
                        attachment.textContent
                            ?: if (
                                !attachment.base64Data
                                    .isNullOrBlank()
                            ) {

                                try {

                                    String(
                                        android.util.Base64.decode(
                                            attachment.base64Data,
                                            android.util.Base64.DEFAULT
                                        ),
                                        Charsets.UTF_8
                                    )

                                } catch (
                                    _: Exception
                                ) {
                                    null
                                }

                            } else {
                                null
                            }

                    if (!content.isNullOrBlank()) {

                        currentParts.put(
                            JSONObject().put(
                                "text",
                                "[Attached Document: " +
                                        "${attachment.name}]\n" +
                                        content
                            )
                        )
                    }
                }
            }
        }

        // ------------------------------------------------------------
        // CURRENT PROMPT
        // ------------------------------------------------------------

        val trimmedPrompt =
            prompt.trim()

        val textToInclude =
            when {

                trimmedPrompt.isNotEmpty() ->
                    trimmedPrompt

                attachments.isNotEmpty() ->
                    resolveDefaultPrompt(
                        attachments
                    )

                else ->
                    "Hello"
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

                if (part.has("text")) {

                    val text =
                        part.optString("text")

                    if (text.isNotEmpty()) {
                        result.append(text)
                    }
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
    ): Pair<
            List<String>,
            List<SearchSource>
            >? {

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

            // ------------------------------------------------------------
            // SEARCH QUERIES
            // ------------------------------------------------------------

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

            // ------------------------------------------------------------
            // SOURCES
            // ------------------------------------------------------------

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
                        chunk.optJSONObject("web")
                            ?: continue

                    val uri =
                        web.optString("uri")

                    val title =
                        web.optString("title")

                    if (uri.isNotBlank()) {

                        sources.add(
                            SearchSource(
                                title =
                                    if (
                                        title.isNotBlank()
                                    ) {
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
            )?.replace(
                apiKey,
                "[REDACTED]"
            )

        val userMessage =
            when (code) {

                // ----------------------------------------------------
                // BAD REQUEST
                // ----------------------------------------------------

                400 ->
                    "Request error (400): " +
                            (
                                    apiMessage
                                        ?: "Invalid request or model configuration."
                                    )

                // ----------------------------------------------------
                // AUTH
                // ----------------------------------------------------

                401, 403 ->
                    "Gemini authentication failed. " +
                            "Please check the API key configuration."

                // ----------------------------------------------------
                // MODEL NOT FOUND
                // ----------------------------------------------------

                404 ->
                    "The Gemini model '$modelId' is unavailable."

                // ----------------------------------------------------
                // RATE LIMIT
                // ----------------------------------------------------

                429 ->
                    "Mayra AI is temporarily busy. Please try again shortly."

                // ----------------------------------------------------
                // SERVICE UNAVAILABLE
                // ----------------------------------------------------

                503 ->
                    "Mayra AI is temporarily unavailable. Please try again shortly."

                // ----------------------------------------------------
                // OTHER SERVER ERRORS
                // ----------------------------------------------------

                in 500..599 ->
                    "Mayra AI service is temporarily busy. Please try again shortly."

                // ----------------------------------------------------
                // OTHER
                // ----------------------------------------------------

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

    // ------------------------------------------------------------------------
    // MODEL UNAVAILABLE
    // ------------------------------------------------------------------------

    private fun isModelUnavailableError(
        throwable: Throwable
    ): Boolean {

        if (throwable is GeminiApiException) {

            return throwable.statusCode == 404 ||
                    throwable.statusCode == 503 ||
                    throwable.statusCode in 500..599
        }

        if (throwable is IOException) {
            return true
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
