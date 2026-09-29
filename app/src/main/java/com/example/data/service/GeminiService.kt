package com.example.data.service

import com.example.BuildConfig
import com.example.domain.model.AiModelConfig
import com.example.domain.model.AiStreamChunk
import com.example.domain.model.Attachment
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
import java.util.concurrent.TimeUnit

/**
 * Production-ready implementation of [AiService] for Google's Gemini models.
 * 
 * Supports:
 * - Real-time SSE token streaming
 * - Configurable model IDs (e.g. gemini-flash-latest, gemini-3.5-flash)
 * - Safe conversational history mapping to user/model roles
 * - Configurable Mayra AI system instruction
 * - Robust transient error retries (exponential backoff for network/429/5xx)
 * - Clean cancellation when a user cancels or starts a new chat
 * - Zero secret leakage (never logs or exposes API keys)
 */
class GeminiService(
    private val apiKeyProvider: () -> String = { BuildConfig.GEMINI_API_KEY },
    private val okHttpClient: OkHttpClient = createDefaultClient()
) : AiService {

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_TRANSIENT_RETRIES = 3
        private const val PLACEHOLDER_KEY = "MY_GEMINI_API_KEY"

        private fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    override suspend fun isAvailable(): Boolean {
        val key = apiKeyProvider().trim().removeSurrounding("\"")
        return key.isNotEmpty() && key != PLACEHOLDER_KEY
    }

    override suspend fun generateResponse(
        conversationId: String,
        prompt: String,
        history: List<ChatMessage>,
        config: AiModelConfig,
        attachments: List<com.example.domain.model.Attachment>,
        enableSearch: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder()
            generateStream(conversationId, prompt, history, config, attachments, enableSearch).collect { chunk ->
                if (!chunk.isComplete) {
                    sb.append(chunk.textDelta)
                }
            }
            val text = sb.toString().trim()
            if (text.isEmpty()) {
                Result.failure(IllegalStateException("Gemini returned an empty response. Please rephrase or try again."))
            } else {
                Result.success(text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun generateStream(
        conversationId: String,
        prompt: String,
        history: List<ChatMessage>,
        config: AiModelConfig,
        attachments: List<com.example.domain.model.Attachment>,
        enableSearch: Boolean
    ): Flow<AiStreamChunk> = flow {
        val apiKey = apiKeyProvider().trim().removeSurrounding("\"")
        if (apiKey.isEmpty() || apiKey == PLACEHOLDER_KEY) {
            throw IllegalStateException(
                "Gemini API key is not configured. Please add your GEMINI_API_KEY in the AI Studio Secrets panel."
            )
        }

        val trimmedPrompt = prompt.trim()
        val effectivePrompt = if (trimmedPrompt.isEmpty() && attachments.isNotEmpty()) {
            resolveDefaultPrompt(attachments)
        } else trimmedPrompt

        if (effectivePrompt.isEmpty()) {
            throw IllegalArgumentException("Message content or attachment cannot be empty.")
        }

        // Notify UI that search grounding is active
        if (enableSearch) {
            emit(
                AiStreamChunk(
                    conversationId = conversationId,
                    textDelta = "",
                    isSearching = true
                )
            )
        }

        // Build candidate models: primary requested model first, followed by available fallback models
        val candidateConfigs = buildList {
            add(config)
            for (available in AiModelConfig.AvailableModels) {
                if (available.modelId != config.modelId && none { it.modelId == available.modelId }) {
                    add(config.copy(modelId = available.modelId, displayName = available.displayName))
                }
            }
        }

        var hasEmittedAnyChunk = false
        var lastException: Exception? = null

        for (currentConfig in candidateConfigs) {
            // Once tokens have reached the user, we cannot switch models or restart
            if (hasEmittedAnyChunk) break

            val isPrimary = (currentConfig.modelId == config.modelId)
            val maxRetriesForThisModel = if (isPrimary) MAX_TRANSIENT_RETRIES else 1
            var attempt = 0

            val requestPayload = buildRequestJson(effectivePrompt, history, currentConfig, attachments, enableSearch)
            val endpoint = "$BASE_URL/${currentConfig.modelId}:streamGenerateContent?alt=sse&key=$apiKey"

            val request = Request.Builder()
                .url(endpoint)
                .post(requestPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "text/event-stream")
                .build()

            while (attempt <= maxRetriesForThisModel) {
                currentCoroutineContext().ensureActive()
                val call = okHttpClient.newCall(request)

                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            handleHttpError(response, apiKey, currentConfig.modelId)
                        }

                        val body = response.body
                            ?: throw IllegalStateException("Gemini server returned an empty response body.")

                        val accumulatedSources = mutableListOf<SearchSource>()
                        val seenUrls = mutableSetOf<String>()
                        val reader = body.byteStream().bufferedReader(Charsets.UTF_8)

                        reader.use {
                            var line: String?
                            while (it.readLine().also { l -> line = l } != null) {
                                currentCoroutineContext().ensureActive()
                                val rawLine = line?.trim() ?: continue
                                if (!rawLine.startsWith("data:")) continue

                                val jsonPayload = rawLine.removePrefix("data:").trim()
                                if (jsonPayload.isEmpty() || jsonPayload == "[DONE]") continue

                                // 1. Parse grounding metadata (web search queries & chunks)
                                val grounding = parseGroundingMetadata(jsonPayload)
                                if (grounding != null) {
                                    val (queries, sources) = grounding
                                    val newSources = mutableListOf<SearchSource>()
                                    for (s in sources) {
                                        if (seenUrls.add(s.url)) {
                                            accumulatedSources.add(s)
                                            newSources.add(s)
                                        }
                                    }
                                    if (queries.isNotEmpty() || newSources.isNotEmpty()) {
                                        emit(
                                            AiStreamChunk(
                                                conversationId = conversationId,
                                                textDelta = "",
                                                searchQueries = queries,
                                                searchSources = accumulatedSources.toList(),
                                                isSearching = false
                                            )
                                        )
                                    }
                                }

                                // 2. Parse text delta
                                val deltaText = parseTextDelta(jsonPayload)
                                if (!deltaText.isNullOrEmpty()) {
                                    hasEmittedAnyChunk = true
                                    emit(
                                        AiStreamChunk(
                                            conversationId = conversationId,
                                            textDelta = deltaText,
                                            isComplete = false,
                                            searchSources = accumulatedSources.toList()
                                        )
                                    )
                                }
                            }
                        }

                        if (!hasEmittedAnyChunk) {
                            throw IllegalStateException("No text content received from Gemini model response.")
                        }

                        emit(
                            AiStreamChunk(
                                conversationId = conversationId,
                                textDelta = "",
                                isComplete = true,
                                searchSources = accumulatedSources.toList()
                            )
                        )
                        return@flow // Completed successfully
                    }
                } catch (e: CancellationException) {
                    call.cancel()
                    throw e
                } catch (e: Exception) {
                    call.cancel()
                    lastException = e

                    // If chunks were already emitted to UI, do not retry or switch models
                    if (hasEmittedAnyChunk) {
                        throw e
                    }

                    val isTransient = isTransientError(e)
                    val isModelUnavailable = isModelUnavailableError(e)

                    if (isTransient && attempt < maxRetriesForThisModel) {
                        attempt++
                        val retryAfterSeconds = (e as? GeminiApiException)?.retryAfterSeconds
                        val backoffMs = if (retryAfterSeconds != null && retryAfterSeconds in 1..60) {
                            retryAfterSeconds * 1000L
                        } else {
                            // Exponential backoff with jitter: ~2s for attempt 1, ~4s for attempt 2, ~8s for attempt 3
                            val baseMs = when (attempt) {
                                1 -> 2000L
                                2 -> 4000L
                                3 -> 8000L
                                else -> 8000L
                            }
                            baseMs + (50L..300L).random()
                        }
                        delay(backoffMs)
                    } else if (isModelUnavailable) {
                        // Current model unavailable after retries; break inner loop to try next candidate fallback model
                        break
                    } else {
                        // Non-transient error (e.g. 401 unauthorized, 400 bad request)
                        throw e
                    }
                }
            }
        }

        // All retries and candidate models failed; throw final exception so UI can display retry button
        throw (lastException ?: IllegalStateException("Mayra AI service is currently unavailable. Please tap Retry."))
    }.flowOn(Dispatchers.IO)

    private fun resolveDefaultPrompt(attachments: List<com.example.domain.model.Attachment>): String {
        val allImages = attachments.isNotEmpty() && attachments.all { it.type == com.example.domain.model.AttachmentType.IMAGE }
        val allPdfs = attachments.isNotEmpty() && attachments.all { it.type == com.example.domain.model.AttachmentType.PDF }
        return when {
            allImages -> "Analyze this image and describe what you can understand from it."
            allPdfs -> "Analyze this document and describe what you can understand from it."
            else -> "Analyze this document and explain the key information."
        }
    }

    /**
     * Constructs the standard Gemini API JSON payload with systemInstruction,
     * generationConfig, multimodal attachment parts, and properly sequenced user/model history turns.
     */
    private fun buildRequestJson(
        prompt: String,
        history: List<ChatMessage>,
        config: AiModelConfig,
        attachments: List<com.example.domain.model.Attachment> = emptyList(),
        enableSearch: Boolean = false
    ): JSONObject {
        val root = JSONObject()

        // 1. System Instruction
        if (config.systemPrompt.isNotBlank()) {
            val sysInstruction = JSONObject()
            val sysParts = JSONArray()
            sysParts.put(JSONObject().put("text", config.systemPrompt))
            sysInstruction.put("parts", sysParts)
            root.put("systemInstruction", sysInstruction)
        }

        // 2. Generation Config
        val genConfig = JSONObject()
        genConfig.put("temperature", config.temperature)
        genConfig.put("maxOutputTokens", config.maxTokens)
        root.put("generationConfig", genConfig)

        // 3. Web Search Grounding Tool
        if (enableSearch) {
            val toolsArray = JSONArray()
            val searchTool = JSONObject().put("google_search", JSONObject())
            toolsArray.put(searchTool)
            root.put("tools", toolsArray)
        }

        // 4. Contents (History turns + newest user prompt & multimodal attachments)
        val contentsArray = JSONArray()

        // Collect prior history messages
        val historyTurns = mutableListOf<Pair<String, List<JSONObject>>>()

        for (msg in history) {
            if (msg.status == MessageStatus.ERROR) continue
            val rawText = msg.content.trim()
            if (rawText.isEmpty() && msg.attachments.isEmpty()) continue

            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "model"
                MessageRole.SYSTEM -> continue
            }

            val displayText = if (msg.attachments.isNotEmpty()) {
                val attNames = msg.attachments.joinToString { it.name }
                if (rawText.isEmpty()) "[Attached: $attNames]" else "[Attached: $attNames]\n$rawText"
            } else rawText

            val part = JSONObject().put("text", displayText)
            historyTurns.add(Pair(role, listOf(part)))
        }

        // Gemini requires the sequence to start with a 'user' turn
        while (historyTurns.isNotEmpty() && historyTurns.first().first != "user") {
            historyTurns.removeAt(0)
        }

        // Add history turns
        for ((role, parts) in historyTurns) {
            val turnObj = JSONObject()
            turnObj.put("role", role)
            val partsArray = JSONArray()
            for (p in parts) {
                partsArray.put(p)
            }
            turnObj.put("parts", partsArray)
            contentsArray.put(turnObj)
        }

        // Add active current turn
        val currentTurn = JSONObject()
        currentTurn.put("role", "user")
        val currentParts = JSONArray()

        for (att in attachments) {
            if (!att.base64Data.isNullOrBlank()) {
                val inlineData = JSONObject()
                inlineData.put("mimeType", att.mimeType)
                inlineData.put("data", att.base64Data)
                currentParts.put(JSONObject().put("inlineData", inlineData))
            } else if (!att.textContent.isNullOrBlank()) {
                currentParts.put(JSONObject().put("text", "[Document: ${att.name}]\n${att.textContent}"))
            }
        }

        val trimmedPrompt = prompt.trim()
        val textToInclude = if (trimmedPrompt.isNotEmpty()) {
            trimmedPrompt
        } else if (attachments.isNotEmpty()) {
            resolveDefaultPrompt(attachments)
        } else {
            "Hello"
        }

        currentParts.put(JSONObject().put("text", textToInclude))
        currentTurn.put("parts", currentParts)
        contentsArray.put(currentTurn)

        root.put("contents", contentsArray)
        return root
    }

    /**
     * Extracts text chunks from candidate parts in Gemini's SSE JSON event.
     */
    private fun parseTextDelta(jsonStr: String): String? {
        return try {
            val root = JSONObject(jsonStr)
            val candidates = root.optJSONArray("candidates") ?: return null
            val firstCandidate = candidates.optJSONObject(0) ?: return null
            val content = firstCandidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null

            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i)
                val text = if (part != null && part.has("text")) part.getString("text") else null
                if (text != null) {
                    sb.append(text)
                }
            }
            if (sb.isNotEmpty()) sb.toString() else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts web search queries and grounding chunk sources from Gemini's candidate metadata.
     */
    private fun parseGroundingMetadata(jsonStr: String): Pair<List<String>, List<SearchSource>>? {
        return try {
            val root = JSONObject(jsonStr)
            val candidates = root.optJSONArray("candidates") ?: return null
            val firstCandidate = candidates.optJSONObject(0) ?: return null
            val groundingMetadata = firstCandidate.optJSONObject("groundingMetadata") ?: return null

            val queries = mutableListOf<String>()
            val queriesArray = groundingMetadata.optJSONArray("webSearchQueries")
            if (queriesArray != null) {
                for (i in 0 until queriesArray.length()) {
                    val q = queriesArray.optString(i)
                    if (q.isNotBlank()) queries.add(q)
                }
            }

            val sources = mutableListOf<SearchSource>()
            val chunksArray = groundingMetadata.optJSONArray("groundingChunks")
            if (chunksArray != null) {
                for (i in 0 until chunksArray.length()) {
                    val chunk = chunksArray.optJSONObject(i) ?: continue
                    val web = chunk.optJSONObject("web") ?: continue
                    val uri = web.optString("uri")
                    val title = web.optString("title")
                    if (uri.isNotBlank()) {
                        sources.add(
                            SearchSource(
                                title = if (title.isNotBlank()) title else SearchSource.extractDomain(uri),
                                url = uri
                            )
                        )
                    }
                }
            }
            Pair(queries, sources)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Safely translates HTTP status codes into user-friendly messages without exposing keys.
     */
    private fun handleHttpError(response: Response, apiKey: String, modelId: String): Nothing {
        val code = response.code
        val rawBody = try {
            response.body?.string()
        } catch (e: Exception) {
            null
        }

        val retryAfter = response.header("Retry-After")?.toLongOrNull()
        val apiMessage = extractErrorFromResponse(rawBody)?.replace(apiKey, "[REDACTED]")

        val userMessage = when (code) {
            400 -> "Request error (400): ${apiMessage ?: "Invalid parameters or model configuration."}"
            401, 403 -> "Authentication failed (401/403): Invalid or unauthorized Gemini API key. Please check your key in the AI Studio Secrets panel."
            404 -> "Model not found (404): The requested model '$modelId' is unavailable. Please select an available model in Settings."
            429 -> "Mayra AI service is experiencing high demand right now (429). Please wait a moment and tap Retry."
            503 -> "Mayra AI service is temporarily unavailable (503). The server is busy or overloaded. Please tap Retry in a few moments."
            in 500..599 -> "Mayra AI service is temporarily busy ($code). Please tap Retry in a few seconds."
            else -> "Gemini error ($code): ${apiMessage ?: "Unexpected response from Gemini service."}"
        }

        throw GeminiApiException(code, userMessage, retryAfter)
    }

    private fun extractErrorFromResponse(rawBody: String?): String? {
        if (rawBody.isNullOrBlank()) return null
        return try {
            val root = JSONObject(rawBody)
            root.optJSONObject("error")?.optString("message")
        } catch (e: Exception) {
            null
        }
    }

    private fun isTransientError(throwable: Throwable): Boolean {
        if (throwable is IOException) return true
        if (throwable is GeminiApiException) {
            return throwable.statusCode == 429 || throwable.statusCode == 503 || throwable.statusCode in 500..599
        }
        return false
    }

    private fun isModelUnavailableError(throwable: Throwable): Boolean {
        if (throwable is GeminiApiException) {
            return throwable.statusCode == 503 ||
                    throwable.statusCode == 404 ||
                    throwable.statusCode == 429 ||
                    throwable.statusCode in 500..599
        }
        if (throwable is IOException) return true
        return false
    }
}

class GeminiApiException(
    val statusCode: Int,
    message: String,
    val retryAfterSeconds: Long? = null
) : Exception(message)
