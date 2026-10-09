
package com.example.data.service

import com.example.domain.model.AiModelConfig
import com.example.domain.model.AiStreamChunk
import com.example.domain.model.Attachment
import com.example.domain.model.ChatMessage
import com.example.domain.service.AiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenAIService(
    private val client: OkHttpClient = createClient()
) : AiService {

    companion object {
        private const val ENDPOINT =
            "https://mayra-ai-six.vercel.app/api/chat"

        private const val DEFAULT_MODEL = "gpt-6-luna"

        private fun createClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(180, TimeUnit.SECONDS)
                .build()
    }

    override suspend fun isAvailable(): Boolean = true

    override suspend fun generateResponse(
        conversationId: String,
        prompt: String,
        history: List<ChatMessage>,
        config: AiModelConfig,
        attachments: List<Attachment>,
        enableSearch: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
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
                .addHeader("Content-Type", "application/json")
                .post(
                    requestJson.toString()
                        .toRequestBody("application/json".toMediaType())
                )
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Mayra API error (${response.code}): ${extractError(body)}"
                        )
                    )
                }

                val output = extractOutputText(body)

                if (output.isBlank()) {
                    Result.failure(
                        IllegalStateException(
                            "Mayra API returned an empty response."
                        )
                    )
                } else {
                    Result.success(output)
                }
            }
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    e.message ?: "Unknown Mayra API error",
                    e
                )
            )
        }
    }

    override fun generateStream(
        conversationId: String,
        prompt: String,
        history: List<ChatMessage>,
        config: AiModelConfig,
        attachments: List<Attachment>,
        enableSearch: Boolean
    ): Flow<AiStreamChunk> = channelFlow {
        try {
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
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .post(
                    requestJson.toString()
                        .toRequestBody("application/json".toMediaType())
                )
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = response.body?.string().orEmpty()

                        send(
                            AiStreamChunk(
                                conversationId = conversationId,
                                textDelta =
                                    "Mayra API error (${response.code}): ${extractError(body)}",
                                isComplete = true
                            )
                        )
                        return@withContext
                    }

                    val source = response.body?.source()

                    if (source == null) {
                        send(
                            AiStreamChunk(
                                conversationId = conversationId,
                                textDelta = "",
                                isComplete = true
                            )
                        )
                        return@withContext
                    }

                    var receivedText = false
                    var completed = false

                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break

                        if (!line.startsWith("data:")) continue

                        val data = line.removePrefix("data:").trim()

                        if (data.isEmpty()) continue

                        if (data == "[DONE]") {
                            completed = true
                            break
                        }

                        val event = parseStreamEvent(data)

                        if (event.first.isNotEmpty()) {
                            receivedText = true

                            send(
                                AiStreamChunk(
                                    conversationId = conversationId,
                                    textDelta = event.first,
                                    isComplete = false
                                )
                            )
                        }

                        if (event.second) {
                            completed = true
                            break
                        }
                    }

                    if (!receivedText || completed) {
                        send(
                            AiStreamChunk(
                                conversationId = conversationId,
                                textDelta = "",
                                isComplete = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            send(
                AiStreamChunk(
                    conversationId = conversationId,
                    textDelta =
                        "Mayra error: ${e.message ?: "Unknown error"}",
                    isComplete = true
                )
            )
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

        val selectedModel = config.modelId
            .takeIf { it.startsWith("gpt-") }
            ?: DEFAULT_MODEL

        root.put("model", selectedModel)
        root.put("stream", stream)
        root.put("instructions", config.systemPrompt)

        val input = JSONArray()

        history.forEach { message ->
            val role = when (message.role.name) {
                "ASSISTANT" -> "assistant"
                "SYSTEM" -> "system"
                else -> "user"
            }

            input.put(
                JSONObject()
                    .put("role", role)
                    .put("content", message.content)
            )
        }

        val content = JSONArray()

        content.put(
            JSONObject()
                .put("type", "input_text")
                .put("text", prompt)
        )

        attachments.forEach { attachment ->
            val base64 = attachment.base64Data
            val mimeType = attachment.mimeType.lowercase()
            val fileName = attachment.name.ifBlank { "attachment" }

            when {
                mimeType.startsWith("image/") &&
                    !base64.isNullOrBlank() -> {
                    content.put(
                        JSONObject()
                            .put("type", "input_image")
                            .put(
                                "image_url",
                                "data:$mimeType;base64,$base64"
                            )
                            .put("detail", "auto")
                    )
                }

                mimeType == "application/pdf" &&
                    !base64.isNullOrBlank() -> {
                    content.put(
                        JSONObject()
                            .put("type", "input_file")
                            .put("filename", fileName)
                            .put(
                                "file_data",
                                "data:application/pdf;base64,$base64"
                            )
                    )
                }

                !attachment.textContent.isNullOrBlank() -> {
                    content.put(
                        JSONObject()
                            .put("type", "input_text")
                            .put(
                                "text",
                                "Attached document: $fileName\n\n${attachment.textContent}"
                            )
                    )
                }

                !base64.isNullOrBlank() -> {
                    content.put(
                        JSONObject()
                            .put("type", "input_file")
                            .put("filename", fileName)
                            .put(
                                "file_data",
                                "data:$mimeType;base64,$base64"
                            )
                    )
                }
            }
        }

        input.put(
            JSONObject()
                .put("role", "user")
                .put("content", content)
        )

        root.put("input", input)

        if (enableSearch) {
            root.put(
                "tools",
                JSONArray().put(
                    JSONObject().put("type", "web_search")
                )
            )
        }

        return root
    }

    private fun parseStreamEvent(data: String): Pair<String, Boolean> {
        return try {
            val json = JSONObject(data)

            when (json.optString("type")) {
                "response.output_text.delta" ->
                    Pair(json.optString("delta", ""), false)

                "response.completed",
                "response.done" ->
                    Pair("", true)

                "response.failed",
                "error" -> {
                    val message = json.optJSONObject("error")
                        ?.optString("message")
                        ?: json.optString(
                            "message",
                            "OpenAI response failed"
                        )

                    Pair("\nMayra error: $message", true)
                }

                else -> Pair("", false)
            }
        } catch (_: Exception) {
            Pair("", false)
        }
    }

    private fun extractOutputText(body: String): String {
        return try {
            val root = JSONObject(body)

            val directText = root.optString("output_text", "")
            if (directText.isNotBlank()) return directText

            val output = root.optJSONArray("output") ?: return ""
            val result = StringBuilder()

            for (i in 0 until output.length()) {
                val item = output.optJSONObject(i) ?: continue
                val content = item.optJSONArray("content") ?: continue

                for (j in 0 until content.length()) {
                    val part = content.optJSONObject(j) ?: continue

                    if (part.optString("type") == "output_text") {
                        val text = part.optString("text", "")
                        if (text.isNotEmpty()) result.append(text)
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
            error?.optString("message", body) ?: body
        } catch (_: Exception) {
            body.ifBlank { "Unknown Mayra API error" }
        }
    }
}
