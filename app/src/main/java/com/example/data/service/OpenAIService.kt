package com.example.data.service

import com.example.domain.model.AiModelConfig
import com.example.domain.model.AiStreamChunk
import com.example.domain.model.Attachment
import com.example.domain.model.ChatMessage
import com.example.domain.model.MessageRole
import com.example.domain.service.AiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Mayra AI - OpenAI Responses API service.
 *
 * This service is intentionally kept separate from GeminiService
 * so the existing Gemini implementation can remain available
 * until the migration is completed.
 *
 * IMPORTANT:
 * The OpenAI API key must NOT be hardcoded in the APK.
 * Production requests should eventually go through a secure backend.
 */
class OpenAIService(
    private val apiKeyProvider: () -> String = {
        ""
    },
    private val client: OkHttpClient = createClient()
) : AiService {

    companion object {
        private const val ENDPOINT =
            "https://api.openai.com/v1/responses"

        private const val DEFAULT_MODEL =
            "gpt-5"

        private fun createClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    override suspend fun isAvailable(): Boolean {
        return apiKeyProvider()
            .trim()
            .isNotEmpty()
    }

    override suspend fun generateResponse(
        conversationId: String,
        prompt: String,
        history: List<ChatMessage>,
        config: AiModelConfig,
        attachments: List<Attachment>,
        enableSearch: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {

        try {
            val apiKey = apiKeyProvider()
                .trim()
                .removeSurrounding("\"")

            if (apiKey.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "OpenAI API is not configured."
                    )
                )
            }

            if (prompt.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "Prompt cannot be empty."
                    )
                )
            }

            val requestJson = buildRequest(
                prompt = prompt,
                history = history,
                config = config,
                attachments = attachments,
                enableSearch = enableSearch,
                stream = false
            )

            val request = Request.Builder()
                .url(ENDPOINT)
                .post(
                    requestJson
                        .toString()
                        .toRequestBody(
                            "application/json; charset=utf-8"
                                .toMediaType()
                        )
                )
                .header(
                    "Authorization",
                    "Bearer $apiKey"
                )
                .header(
                    "Content-Type",
                    "application/json"
                )
                .build()

            client.newCall(request).execute().use { response ->

                val body = response.body
                    ?.string()
                    .orEmpty()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        OpenAIException(
                            response.code,
                            extractError(body)
                                ?: "OpenAI request failed."
                        )
                    )
                }

                val text = extractOutputText(body)

                if (text.isBlank()) {
                    return@withContext Result.failure(
                        OpenAIException(
                            0,
                            "OpenAI returned an empty response."
                        )
                    )
                }

                Result.success(text)
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

        if (apiKey.isBlank()) {
            emit(
                AiStreamChunk(
                    conversationId = conversationId,
                    textDelta = "OpenAI API is not configured.",
                    isComplete = true
                )
            )
            return@flow
        }

        try {
            val requestJson = buildRequest(
                prompt = prompt,
                history = history,
                config = config,
                attachments = attachments,
                enableSearch = enableSearch,
                stream = true
            )

            val request = Request.Builder()
                .url(ENDPOINT)
                .post(
                    requestJson
                        .toString()
                        .toRequestBody(
                            "application/json; charset=utf-8"
                                .toMediaType()
                        )
                )
                .header(
                    "Authorization",
                    "Bearer $apiKey"
                )
                .header(
                    "Content-Type",
                    "application/json"
                )
                .header(
                    "Accept",
                    "text/event-stream"
                )
                .build()

            client.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    val body = response.body
                        ?.string()
                        .orEmpty()

                    emit(
                        AiStreamChunk(
                            conversationId = conversationId,
                            textDelta =
                                extractError(body)
                                    ?: "OpenAI request failed.",
                            isComplete = true
                        )
                    )

                    return@flow
                }

                val source = response.body
                    ?.source()

                if (source == null) {
                    emit(
                        AiStreamChunk(
                            conversationId = conversationId,
                            textDelta = "Empty OpenAI response.",
                            isComplete = true
                        )
                    )
                    return@flow
                }

                var completed = false

                while (!source.exhausted()) {

                    val line = source
                        .readUtf8Line()
                        ?: break

                    if (!line.startsWith("data:")) {
                        continue
                    }

                    val data = line
                        .removePrefix("data:")
                        .trim()

                    if (data == "[DONE]") {
                        completed = true

                        emit(
                            AiStreamChunk(
                                conversationId = conversationId,
                                textDelta = "",
                                isComplete = true
                            )
                        )

                        break
                    }

                    val delta = parseStreamDelta(data)

                    if (delta.isNotEmpty()) {
                        emit(
                            AiStreamChunk(
                                conversationId = conversationId,
                                textDelta = delta,
                                isComplete = false
                            )
                        )
                    }
                }

                if (!completed) {
                    emit(
                        AiStreamChunk(
                            conversationId = conversationId,
                            textDelta = "",
                            isComplete = true
                        )
                    )
                }
            }

        } catch (e: Exception) {

            emit(
                AiStreamChunk(
                    conversationId = conversationId,
                    textDelta =
                        e.message
                            ?: "OpenAI connection failed.",
                    isComplete = true
                )
            )
        }
    }

    private fun buildRequest(
        prompt: String,
        history: List<ChatMessage>,
        config: AiModelConfig,
        attachments: List<Attachment>,
        enableSearch: Boolean,
        stream: Boolean
    ): JSONObject {

        val root = JSONObject()

        root.put(
            "model",
            DEFAULT_MODEL
        )

        root.put(
            "stream",
            stream
        )

        val instructions =
            config.systemPrompt.trim()

        if (instructions.isNotEmpty()) {
            root.put(
                "instructions",
                instructions
            )
        }

        val input = JSONArray()

        for (message in history) {

            if (message.content.isBlank()) {
                continue
            }

            val role =
                when (message.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.SYSTEM -> "system"
                }

            input.put(
                JSONObject().apply {
                    put("role", role)
                    put(
                        "content",
                        message.content
                    )
                }
            )
        }

        input.put(
            JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }
        )

        root.put(
            "input",
            input
        )

        if (enableSearch) {
            root.put(
                "tools",
                JSONArray().put(
                    JSONObject().apply {
                        put(
                            "type",
                            "web_search"
                        )
                    }
                )
            )
        }

        return root
    }

    private fun extractOutputText(
        body: String
    ): String {

        return try {
            val root = JSONObject(body)

            root.optString(
                "output_text",
                ""
            ).trim()

        } catch (_: Exception) {
            ""
        }
    }

    private fun parseStreamDelta(
        data: String
    ): String {

        return try {
            val root = JSONObject(data)

            root.optString(
                "delta",
                ""
            )

        } catch (_: Exception) {
            ""
        }
    }

    private fun extractError(
        body: String
    ): String? {

        return try {
            val root = JSONObject(body)

            root.optJSONObject("error")
                ?.optString("message")
                ?.takeIf {
                    it.isNotBlank()
                }

        } catch (_: Exception) {
            null
        }
    }
}

class OpenAIException(
    val statusCode: Int,
    message: String
) : Exception(message)
