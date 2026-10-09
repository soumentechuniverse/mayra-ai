package com.example.data.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeneratedImageResult(
    val base64Data: String,
    val mimeType: String = "image/png",
    val text: String? = null
)

class ImageGenerationService(
    private val client: OkHttpClient = createClient()
) {

    companion object {

        private const val ENDPOINT =
            "https://mayra-ai-six.vercel.app/api/images"

        private const val IMAGE_MODEL =
            "gpt-image-1"

        private fun createClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(180, TimeUnit.SECONDS)
                .build()
    }

    suspend fun generateImage(
        prompt: String,
        aspectRatio: String = "1:1",
        imageSize: String = "1K"
    ): Result<GeneratedImageResult> =
        withContext(Dispatchers.IO) {

            try {

                val size = resolveSize(
                    aspectRatio = aspectRatio,
                    imageSize = imageSize
                )

                val requestJson =
                    JSONObject()
                        .put("model", IMAGE_MODEL)
                        .put("prompt", prompt)
                        .put("size", size)
                        .put("quality", "auto")
                        .put("output_format", "png")

                val request =
                    Request.Builder()
                        .url(ENDPOINT)
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

                client.newCall(request)
                    .execute()
                    .use { response ->

                        val body =
                            response.body
                                ?.string()
                                .orEmpty()

                        if (!response.isSuccessful) {

                            return@withContext Result.failure(
                                IllegalStateException(
                                    "Mayra image API error " +
                                        "(${response.code}): " +
                                        extractError(body)
                                )
                            )
                        }

                        val imageBase64 =
                            extractImageBase64(body)

                        if (imageBase64.isBlank()) {

                            return@withContext Result.failure(
                                IllegalStateException(
                                    "Image generation returned no image data."
                                )
                            )
                        }

                        Result.success(
                            GeneratedImageResult(
                                base64Data = imageBase64,
                                mimeType = "image/png",
                                text = null
                            )
                        )
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

    private fun resolveSize(
        aspectRatio: String,
        imageSize: String
    ): String {

        return when (aspectRatio) {

            "16:9",
            "4:3",
            "3:2" ->
                "1536x1024"

            "9:16",
            "3:4",
            "2:3" ->
                "1024x1536"

            else ->
                "1024x1024"
        }
    }

    private fun extractImageBase64(
        body: String
    ): String {

        return try {

            val root =
                JSONObject(body)

            val data =
                root.optJSONArray("data")
                    ?: return ""

            if (data.length() == 0) {
                return ""
            }

            val first =
                data.optJSONObject(0)
                    ?: return ""

            first.optString(
                "b64_json",
                ""
            )

        } catch (_: Exception) {
            ""
        }
    }

    private fun extractError(
        body: String
    ): String {

        return try {

            val root =
                JSONObject(body)

            val error =
                root.optJSONObject("error")

            error?.optString(
                "message",
                body
            ) ?: body

        } catch (_: Exception) {

            body.ifBlank {
                "Unknown Mayra image API error"
            }
        }
    }
}
