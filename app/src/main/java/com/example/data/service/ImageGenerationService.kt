package com.example.data.service

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

class ImageGenerationService(
    private val apiKeyProvider: () -> String = { BuildConfig.OPENAI_API_KEY },
    private val client: OkHttpClient = createClient()
) {

    companion object {
        private const val ENDPOINT = "https://api.openai.com/v1/responses"
        private const val IMAGE_MODEL = "gpt-image-2.5-flare"

        private fun createClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(180, TimeUnit.SECONDS)
                .build()
    }

    suspend fun generateImage(
        prompt: String,
        size: String = "1024x1024"
    ): Result<String> = withContext(Dispatchers.IO) {

        try {
            val apiKey = apiKeyProvider()
                .trim()
                .removeSurrounding("\"")

            if (apiKey.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "OpenAI API key is not configured."
                    )
                )
            }

            val imageTool = JSONObject()
                .put("type", "image_generation")
                .put("model", IMAGE_MODEL)
                .put("size", size)
                .put("quality", "auto")

            val tools = JSONArray()
                .put(imageTool)

            val input = JSONArray()
                .put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "content",
                            JSONArray().put(
                                JSONObject()
                                    .put("type", "input_text")
                                    .put("text", prompt)
                            )
                        )
                )

            val requestJson = JSONObject()
                .put("model", "gpt-6-luna")
                .put("input", input)
                .put("tools", tools)

            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader(
                    "Authorization",
                    "Bearer $apiKey"
                )
                .addHeader(
                    "Content-Type",
                    "application/json"
                )
                .post(
                    requestJson
                        .toString()
                        .toRequestBody(
                            "application/json".toMediaType()
                        )
                )
                .build()

            client.newCall(request).execute().use { response ->

                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "OpenAI image API error (${response.code}): " +
                                extractError(body)
                        )
                    )
                }

                val imageBase64 = extractImageBase64(body)

                if (imageBase64.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Image generation returned no image data."
                        )
                    )
                }

                Result.success(imageBase64)
            }

        } catch (e: Exception) {

            Result.failure(
                IllegalStateException(
                    "Image generation failed: " +
                        (e.message ?: "Unknown error"),
                    e
                )
            )
        }
    }

    private fun extractImageBase64(body: String): String {

        return try {

            val root = JSONObject(body)

            val output = root.optJSONArray("output")
                ?: return ""

            for (i in 0 until output.length()) {

                val item = output.optJSONObject(i)
                    ?: continue

                if (
                    item.optString("type") ==
                    "image_generation_call"
                ) {

                    val result = item.optString(
                        "result",
                        ""
                    )

                    if (result.isNotEmpty()) {
                        return result
                    }
                }
            }

            ""
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
                "Unknown OpenAI image API error"
            }
        }
    }
}
