package com.example.data.service

import com.example.domain.model.AiModelConfig
import com.example.domain.model.AiStreamChunk
import com.example.domain.model.Attachment
import com.example.domain.model.ChatMessage
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

class OpenAIService(
    private val apiKeyProvider: () -> String = { "" },
    private val client: OkHttpClient = createClient()
) : AiService {

    companion object {
        private const val ENDPOINT = "https://api.openai.com/v1/responses"
        private const val DEFAULT_MODEL = "gpt-5"

        private fun createClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()
        }
    }

    override suspend fun isAvailable(): Boolean {
        return apiKeyProvider().trim().removeSurrounding("\"").isNotEmpty()
    }

    override suspend fun generateResponse(
        conversationId: String,
        prompt: String,
        history: List<ChatMessage>,
        attachments: List<Attachment>,
        config: AiModelConfig,
        enableSearch: Boolean
    ): String = withContext(Dispatchers.IO) {

        val apiKey = apiKeyProvider()
            .trim()
            .removeSurrounding("\"")

        require(apiKey.isNotEmpty()) {
            "OpenAI API key is not configured."
        }

        val requestJson = buildRequest(
            prompt = prompt,
            history = history,
            attachments = attachments,
            config = config,
            enableSearch = enableSearch,
            stream = false
        )

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(
                requestJson
                    .toString()
                    .toRequestBody("application/json".toMediaType())
            )
            .build()

        client.newCall(request).execute().use { response ->

            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "OpenAI API error (${response.code}): ${extractError(body)}"
                )
            }

            extractOutputText(body)
        }
    }

    override fun generateStream(
        conversationId: String,
        prompt: String,
        history: List<ChatMessage>,
        attachments: List<Attachment>,
        config: AiModelConfig,
        enableSearch: Boolean
    ): Flow<AiStreamChunk> = flow {

        val apiKey = apiKeyProvider()
            .trim()
            .removeSurrounding("\"")

        if (apiKey.isEmpty()) {
            emit(
                AiStreamChunk(
                    conversationId = conversationId,
                    textDelta = "OpenAI API key is not configured.",
                    isComplete = true
                )
            )
            return@flow
        }

        val requestJson = buildRequest(
            prompt = prompt,
            history = history,
            attachments = attachments,
            config = config,
            enableSearch = enableSearch,
            stream = true
        )

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(
                requestJson
                    .toString()
                    .toRequestBody("application/json".toMediaType())
            )
            .build()

        withContext(Dispatchers.IO) {

            client.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()

                    emit(
                        AiStreamChunk(
                            conversationId = conversationId,
                            textDelta =
                                "OpenAI API error (${response.code}): ${extractError(body)}",
                            isComplete = true
                        )
                    )

                    return@withContext
                }

                val source = response.body?.source()

                if (source == null) {
                    emit(
                        AiStreamChunk(
                            conversationId = conversationId,
                            textDelta = "",
                            isComplete = true
                        )
                    )
                    return@withContext
                }

                while (!source.exhausted()) {

                    val line = source.readUtf8Line() ?: break

                    if (!line.startsWith("data:")) {
                        continue
                    }

                    val data = line
                        .removePrefix("data:")
                        .trim()

                    if (data.isEmpty()) {
                        continue
                    }

                    if (data == "[DONE]") {
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
            }
        }
    }

    private fun buildRequest(
        prompt: String,
        history: List<ChatMessage>,
        attachments: List<Attachment>,
        config: AiModelConfig,
        enableSearch: Boolean,
        stream: Boolean
    ): JSONObject {

        val root = JSONObject()

        root.put(
            "model",
            if (config.modelId.startsWith("gpt-")) {
                config.modelId
            } else {
                DEFAULT_MODEL
            }
        )

        root.put("stream", stream)

        root.put(
            "instructions",
            config.systemPrompt
        )

        val input = JSONArray()

        history.forEach { message ->

            val role = when (message.role.name) {
                "ASSISTANT" -> "assistant"
                "SYSTEM" -> "system"
                else -> "user"
            }

            val messageObject = JSONObject()

            messageObject.put("role", role)
            messageObject.put(
                "content",
                message.content
            )

            input.put(messageObject)
        }

        val currentMessage = JSONObject()
        currentMessage.put("role", "user")

        val content = JSONArray()

        val textPart = JSONObject()
        textPart.put("type", "input_text")
        textPart.put("text", prompt)

        content.put(textPart)

        /*
         * Image attachments
         */
        attachments.forEach { attachment ->

            val base64 = attachment.base64Data

            if (
                base64 != null &&
                attachment.mimeType.startsWith("image/")
            ) {

                val imagePart = JSONObject()

                imagePart.put(
                    "type",
                    "input_image"
                )

                imagePart.put(
                    "image_url",
                    "data:${attachment.mimeType};base64,$base64"
                )

                content.put(imagePart)
            }
        }

        currentMessage.put(
            "content",
            content
        )

        input.put(currentMessage)

        root.put("input", input)

        /*
         * Web search
         */
        if (enableSearch) {

            val tools = JSONArray()

            val searchTool = JSONObject()

            searchTool.put(
                "type",
                "web_search"
            )

            tools.put(searchTool)

            root.put("tools", tools)
        }

        return root
    }

    private fun parseStreamDelta(data: String): String {

        return try {

            val json = JSONObject(data)

            val type = json.optString("type")

            when (type) {

                "response.output_text.delta" -> {
                    json.optString("delta", "")
                }

                else -> {
                    json.optString("delta", "")
                }
            }

        } catch (_: Exception) {
            ""
        }
    }

    private fun extractOutputText(body: String): String {

        return try {

            val root = JSONObject(body)

            val directText = root.optString(
                "output_text",
                ""
            )

            if (directText.isNotEmpty()) {
                return directText
            }

            val output = root.optJSONArray("output")
                ?: return ""

            val result = StringBuilder()

            for (i in 0 until output.length()) {

                val item = output.optJSONObject(i)
                    ?: continue

                val content = item.optJSONArray("content")
                    ?: continue

                for (j in 0 until content.length()) {

                    val part = content.optJSONObject(j)
                        ?: continue

                    val text = part.optString(
                        "text",
                        ""
                    )

                    if (text.isNotEmpty()) {
                        result.append(text)
                    }
                }
            }

            result.toString()

        } catch (_: Exception) {
            ""
        }
    }

    private fun extractError(body: String): String {

        return try {

            val root = JSONObject(body)

            val error = root.optJSONObject("error")

            error?.optString(
                "message",
                body
            ) ?: body

        } catch (_: Exception) {
            body.ifBlank {
                "Unknown OpenAI API error"
            }
        }
    }
}
