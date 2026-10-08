package com.example.data.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Mayra AI - OpenAI Image Generation Service
 *
 * Uses OpenAI Responses API image_generation tool.
 *
 * Supports:
 * - Image generation
 * - Image editing
 * - Multiple input images
 * - Aspect ratio
 * - Image size
 * - Base64 output
 */
class ImageGenerationService(
    private val apiKeyProvider: () -> String = {
        BuildConfig.OPENAI_API_KEY
    },
    private val client: OkHttpClient = createDefaultClient()
) {

    companion object {

        private const val ENDPOINT =
            "https://api.openai.com/v1/responses"

        private const val MODEL =
            "gpt-image-1"

        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8".toMediaType()

        private fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    data class ImageResult(
        val base64Data: String,
        val mimeType: String = "image/png",
        val text: String? = null
    ) {
        fun toBitmap(): Bitmap? {
            return try {
                val bytes = Base64.decode(
                    base64Data,
                    Base64.DEFAULT
                )

                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    data class InputImage(
        val base64Data: String,
        val mimeType: String = "image/jpeg"
    )

    suspend fun generateImage(
        prompt: String,
        aspectRatio: String = "1:1",
        imageSize: String = "1K"
    ): Result<ImageResult> {

        return generateInternal(
            prompt = prompt,
            inputImages = emptyList(),
            aspectRatio = aspectRatio,
            imageSize = imageSize
        )
    }

    suspend fun editImage(
        prompt: String,
        imageBase64: String,
        imageMimeType: String = "image/jpeg",
        aspectRatio: String = "1:1",
        imageSize: String = "1K"
    ): Result<ImageResult> {

        return generateInternal(
            prompt = prompt,
            inputImages = listOf(
                InputImage(
                    base64Data = imageBase64,
                    mimeType = imageMimeType
                )
            ),
            aspectRatio = aspectRatio,
            imageSize = imageSize
        )
    }

    suspend fun editImages(
        prompt: String,
        images: List<InputImage>,
        aspectRatio: String = "1:1",
        imageSize: String = "1K"
    ): Result<ImageResult> {

        if (images.isEmpty()) {
            return Result.failure(
                IllegalArgumentException(
                    "At least one image is required."
                )
            )
        }

        return generateInternal(
            prompt = prompt,
            inputImages = images,
            aspectRatio = aspectRatio,
            imageSize = imageSize
        )
    }

    private suspend fun generateInternal(
        prompt: String,
        inputImages: List<InputImage>,
        aspectRatio: String,
        imageSize: String
    ): Result<ImageResult> = withContext(Dispatchers.IO) {

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

            val cleanPrompt = prompt.trim()

            if (cleanPrompt.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "Image prompt cannot be empty."
                    )
                )
            }

            val size = convertImageSize(
                aspectRatio = aspectRatio,
                imageSize = imageSize
            )

            val inputContent = JSONArray()

            inputContent.put(
                JSONObject()
                    .put("type", "input_text")
                    .put("text", cleanPrompt)
            )

            for (image in inputImages) {

                if (image.base64Data.isBlank()) {
                    continue
                }

                val mimeType =
                    normalizeMimeType(image.mimeType)

                inputContent.put(
                    JSONObject()
                        .put("type", "input_image")
                        .put(
                            "image_url",
                            "data:$mimeType;base64,${image.base64Data}"
                        )
                )
            }

            val userMessage =
                JSONObject()
                    .put("role", "user")
                    .put("content", inputContent)

            val input =
                JSONArray().put(userMessage)

            val imageTool =
                JSONObject()
                    .put(
                        "type",
                        "image_generation"
                    )
                    .put(
                        "model",
                        MODEL
                    )
                    .put(
                        "size",
                        size
                    )
                    .put(
                        "quality",
                        "auto"
                    )
                    .put(
                        "output_format",
                        "png"
                    )
                    .put(
                        "action",
                        if (inputImages.isEmpty()) {
                            "generate"
                        } else {
                            "edit"
                        }
                    )

            val requestJson =
                JSONObject()
                    .put(
                        "model",
                        "gpt-5"
                    )
                    .put(
                        "input",
                        input
                    )
                    .put(
                        "tools",
                        JSONArray().put(imageTool)
                    )

            val request =
                Request.Builder()
                    .url(ENDPOINT)
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
                        "application/json"
                    )
                    .post(
                        requestJson
                            .toString()
                            .toRequestBody(
                                JSON_MEDIA_TYPE
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

                        val message =
                            extractErrorMessage(body)

                        return@withContext Result.failure(
                            ImageGenerationException(
                                statusCode =
                                    response.code,
                                message =
                                    message
                                        ?: friendlyError(
                                            response.code
                                        )
                            )
                        )
                    }

                    if (body.isBlank()) {
                        return@withContext Result.failure(
                            ImageGenerationException(
                                statusCode = 0,
                                message =
                                    "OpenAI returned an empty image response."
                            )
                        )
                    }

                    val result =
                        parseImageResponse(body)

                    if (result == null) {
                        return@withContext Result.failure(
                            ImageGenerationException(
                                statusCode = 0,
                                message =
                                    "OpenAI did not return a generated image."
                            )
                        )
                    }

                    Result.success(result)
                }

        } catch (e: IOException) {

            Result.failure(
                ImageGenerationException(
                    statusCode = 0,
                    message =
                        "Network connection failed. Please try again.",
                    cause = e
                )
            )

        } catch (e: Exception) {

            Result.failure(e)
        }
    }

    private fun convertImageSize(
        aspectRatio: String,
        imageSize: String
    ): String {

        val ratio = aspectRatio.trim()

        val size = imageSize.uppercase()

        return when (ratio) {

            "1:1" -> "1024x1024"

            "16:9" ->
                if (size == "2K" || size == "4K") {
                    "1536x1024"
                } else {
                    "1536x1024"
                }

            "9:16" ->
                "1024x1536"

            "4:3" ->
                "1536x1024"

            "3:4" ->
                "1024x1536"

            "3:2" ->
                "1536x1024"

            "2:3" ->
                "1024x1536"

            else ->
                "1024x1024"
        }
    }

    private fun parseImageResponse(
        responseBody: String
    ): ImageResult? {

        return try {

            val root =
                JSONObject(responseBody)

            val output =
                root.optJSONArray("output")
                    ?: return null

            for (i in 0 until output.length()) {

                val item =
                    output.optJSONObject(i)
                        ?: continue

                if (
                    item.optString("type")
                        != "image_generation_call"
                ) {
                    continue
                }

                val status =
                    item.optString(
                        "status",
                        ""
                    )

                val result =
                    item.optString(
                        "result",
                        ""
                    )

                if (
                    status == "completed" &&
                    result.isNotBlank()
                ) {

                    return ImageResult(
                        base64Data = result,
                        mimeType = "image/png",
                        text =
                            root.optString(
                                "output_text",
                                null
                            )
                    )
                }
            }

            null

        } catch (_: Exception) {

            null
        }
    }

    private fun extractErrorMessage(
        responseBody: String
    ): String? {

        if (responseBody.isBlank()) {
            return null
        }

        return try {

            val root =
                JSONObject(responseBody)

            val error =
                root.optJSONObject("error")

            error
                ?.optString("message")
                ?.takeIf {
                    it.isNotBlank()
                }

        } catch (_: Exception) {

            null
        }
    }

    private fun friendlyError(
        statusCode: Int
    ): String {

        return when (statusCode) {

            400 ->
                "Invalid image request."

            401, 403 ->
                "OpenAI API authentication failed."

            404 ->
                "OpenAI image generation service is unavailable."

            429 ->
                "Image generation is temporarily busy. Please try again shortly."

            500, 502, 503, 504 ->
                "OpenAI image generation service is temporarily unavailable."

            else ->
                "Image generation failed. Please try again."
        }
    }

    private fun normalizeMimeType(
        mimeType: String
    ): String {

        return when {

            mimeType.equals(
                "image/jpg",
                ignoreCase = true
            ) ->
                "image/jpeg"

            mimeType.equals(
                "image/jpeg",
                ignoreCase = true
            ) ->
                "image/jpeg"

            mimeType.equals(
                "image/webp",
                ignoreCase = true
            ) ->
                "image/webp"

            mimeType.equals(
                "image/png",
                ignoreCase = true
            ) ->
                "image/png"

            else ->
                "image/jpeg"
        }
    }

    suspend fun bitmapToBase64(
        bitmap: Bitmap,
        quality: Int = 90
    ): String = withContext(
        Dispatchers.Default
    ) {

        val stream =
            ByteArrayOutputStream()

        bitmap.compress(
            Bitmap.CompressFormat.JPEG,
            quality.coerceIn(1, 100),
            stream
        )

        Base64.encodeToString(
            stream.toByteArray(),
            Base64.NO_WRAP
        )
    }
}

class ImageGenerationException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null
) : Exception(
    message,
    cause
)
