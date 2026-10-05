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
 * Mayra AI - Gemini Image Generation Service
 *
 * Uses Google's Gemini 3.1 Flash Image model.
 *
 * Supports:
 * - AI image generation
 * - Image editing
 * - Image transformation
 * - Image-to-image generation
 * - Multiple input images
 * - Aspect ratio selection
 * - 1K / 2K / 4K output
 */
class ImageGenerationService(
    private val apiKeyProvider: () -> String = {
        BuildConfig.GEMINI_API_KEY
    },
    private val client: OkHttpClient = createDefaultClient()
) {

    companion object {

        private const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/interactions"

        private const val MODEL =
            "gemini-3.1-flash-image"

        private const val PLACEHOLDER_KEY =
            "MY_GEMINI_API_KEY"

        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8".toMediaType()

        private fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(
                    20,
                    TimeUnit.SECONDS
                )
                .readTimeout(
                    180,
                    TimeUnit.SECONDS
                )
                .writeTimeout(
                    60,
                    TimeUnit.SECONDS
                )
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    // ========================================================================
    // RESULT
    // ========================================================================

    data class ImageResult(
        val base64Data: String,
        val mimeType: String = "image/png",
        val text: String? = null
    ) {

        /**
         * Convert generated Base64 image into Android Bitmap.
         */
        fun toBitmap(): Bitmap? {

            return try {

                val bytes =
                    Base64.decode(
                        base64Data,
                        Base64.DEFAULT
                    )

                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size
                )

            } catch (
                _: Exception
            ) {

                null
            }
        }
    }

    // ========================================================================
    // GENERATE IMAGE
    // ========================================================================

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

    // ========================================================================
    // EDIT IMAGE
    // ========================================================================

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

    // ========================================================================
    // EDIT MULTIPLE IMAGES
    // ========================================================================

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

    // ========================================================================
    // INPUT IMAGE
    // ========================================================================

    data class InputImage(
        val base64Data: String,
        val mimeType: String = "image/jpeg"
    )

    // ========================================================================
    // CORE REQUEST
    // ========================================================================

    private suspend fun generateInternal(
        prompt: String,
        inputImages: List<InputImage>,
        aspectRatio: String,
        imageSize: String
    ): Result<ImageResult> = withContext(
        Dispatchers.IO
    ) {

        try {

            val apiKey =
                apiKeyProvider()
                    .trim()
                    .removeSurrounding("\"")

            // ------------------------------------------------------------
            // API KEY
            // ------------------------------------------------------------

            if (
                apiKey.isEmpty() ||
                apiKey == PLACEHOLDER_KEY ||
                apiKey.length <= 10
            ) {

                return@withContext Result.failure(
                    IllegalStateException(
                        "Gemini API key is not configured."
                    )
                )
            }

            // ------------------------------------------------------------
            // PROMPT
            // ------------------------------------------------------------

            val cleanPrompt =
                prompt.trim()

            if (cleanPrompt.isEmpty()) {

                return@withContext Result.failure(
                    IllegalArgumentException(
                        "Image prompt cannot be empty."
                    )
                )
            }

            // ------------------------------------------------------------
            // VALIDATE SIZE
            // ------------------------------------------------------------

            val validSize =
                when (imageSize.uppercase()) {

                    "512" -> "512"

                    "1K" -> "1K"

                    "2K" -> "2K"

                    "4K" -> "4K"

                    else -> "1K"
                }

            // ------------------------------------------------------------
            // VALIDATE ASPECT RATIO
            // ------------------------------------------------------------

            val validAspectRatio =
                when (aspectRatio) {

                    "1:1",
                    "1:4",
                    "1:8",
                    "2:3",
                    "3:2",
                    "3:4",
                    "4:1",
                    "4:3",
                    "4:5",
                    "5:4",
                    "8:1",
                    "9:16",
                    "16:9",
                    "21:9" -> aspectRatio

                    else -> "1:1"
                }

            // ------------------------------------------------------------
            // REQUEST INPUT
            // ------------------------------------------------------------

            val input =
                if (inputImages.isEmpty()) {

                    JSONObject().apply {

                        put(
                            "model",
                            MODEL
                        )

                        put(
                            "input",
                            cleanPrompt
                        )

                        put(
                            "response_format",
                            buildImageResponseFormat(
                                aspectRatio =
                                    validAspectRatio,
                                imageSize =
                                    validSize
                            )
                        )
                    }

                } else {

                    JSONObject().apply {

                        put(
                            "model",
                            MODEL
                        )

                        put(
                            "input",
                            buildMultimodalInput(
                                prompt = cleanPrompt,
                                images = inputImages
                            )
                        )

                        put(
                            "response_format",
                            buildImageResponseFormat(
                                aspectRatio =
                                    validAspectRatio,
                                imageSize =
                                    validSize
                            )
                        )
                    }
                }

            // ------------------------------------------------------------
            // HTTP REQUEST
            // ------------------------------------------------------------

            val request =
                Request.Builder()
                    .url(ENDPOINT)
                    .post(
                        input
                            .toString()
                            .toRequestBody(
                                JSON_MEDIA_TYPE
                            )
                    )
                    .header(
                        "x-goog-api-key",
                        apiKey
                    )
                    .header(
                        "Content-Type",
                        "application/json"
                    )
                    .header(
                        "Accept",
                        "application/json"
                    )
                    .build()

            // ------------------------------------------------------------
            // EXECUTE
            // ------------------------------------------------------------

            client.newCall(
                request
            ).execute().use { response ->

                val responseBody =
                    response.body?.string()
                        .orEmpty()

                // --------------------------------------------------------
                // HTTP ERROR
                // --------------------------------------------------------

                if (!response.isSuccessful) {

                    val message =
                        extractErrorMessage(
                            responseBody
                        )

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

                // --------------------------------------------------------
                // EMPTY RESPONSE
                // --------------------------------------------------------

                if (responseBody.isBlank()) {

                    return@withContext Result.failure(
                        ImageGenerationException(
                            statusCode = 0,
                            message =
                                "Gemini returned an empty image response."
                        )
                    )
                }

                // --------------------------------------------------------
                // PARSE IMAGE
                // --------------------------------------------------------

                val result =
                    parseImageResponse(
                        responseBody
                    )

                if (result == null) {

                    return@withContext Result.failure(
                        ImageGenerationException(
                            statusCode = 0,
                            message =
                                "Gemini did not return a generated image."
                        )
                    )
                }

                Result.success(
                    result
                )
            }

        } catch (
            e: IOException
        ) {

            Result.failure(
                ImageGenerationException(
                    statusCode = 0,
                    message =
                        "Network connection failed. Please try again.",
                    cause = e
                )
            )

        } catch (
            e: Exception
        ) {

            Result.failure(e)
        }
    }

    // ========================================================================
    // RESPONSE FORMAT
    // ========================================================================

    private fun buildImageResponseFormat(
        aspectRatio: String,
        imageSize: String
    ): JSONObject {

        return JSONObject().apply {

            put(
                "type",
                "image"
            )

            put(
                "mime_type",
                "image/png"
            )

            put(
                "aspect_ratio",
                aspectRatio
            )

            put(
                "image_size",
                imageSize
            )
        }
    }

    // ========================================================================
    // MULTIMODAL INPUT
    // ========================================================================

    private fun buildMultimodalInput(
        prompt: String,
        images: List<InputImage>
    ): JSONArray {

        val input =
            JSONArray()

        // ------------------------------------------------------------
        // TEXT
        // ------------------------------------------------------------

        input.put(
            JSONObject().apply {

                put(
                    "type",
                    "text"
                )

                put(
                    "text",
                    prompt
                )
            }
        )

        // ------------------------------------------------------------
        // IMAGES
        // ------------------------------------------------------------

        for (image in images) {

            if (
                image.base64Data.isBlank()
            ) {
                continue
            }

            input.put(
                JSONObject().apply {

                    put(
                        "type",
                        "image"
                    )

                    put(
                        "mime_type",
                        normalizeMimeType(
                            image.mimeType
                        )
                    )

                    put(
                        "data",
                        image.base64Data
                    )
                }
            )
        }

        return input
    }

    // ========================================================================
    // PARSE RESPONSE
    // ========================================================================

    private fun parseImageResponse(
        responseBody: String
    ): ImageResult? {

        return try {

            val root =
                JSONObject(responseBody)

            // ------------------------------------------------------------
            // CURRENT INTERACTIONS API FORMAT
            // ------------------------------------------------------------

            val outputImage =
                root.optJSONObject(
                    "output_image"
                )

            if (outputImage != null) {

                val data =
                    outputImage.optString(
                        "data",
                        ""
                    )

                val mimeType =
                    outputImage.optString(
                        "mime_type",
                        "image/png"
                    )

                if (data.isNotBlank()) {

                    return ImageResult(
                        base64Data = data,
                        mimeType = mimeType,
                        text =
                            root.optString(
                                "output_text",
                                null
                            )
                    )
                }
            }

            // ------------------------------------------------------------
            // OUTPUT ARRAY FORMAT
            // ------------------------------------------------------------

            val output =
                root.optJSONArray(
                    "output"
                )

            if (output != null) {

                for (
                    i in 0 until output.length()
                ) {

                    val item =
                        output.optJSONObject(i)
                            ?: continue

                    val type =
                        item.optString(
                            "type",
                            ""
                        )

                    if (
                        type == "image"
                    ) {

                        val data =
                            item.optString(
                                "data",
                                ""
                            )

                        val mimeType =
                            item.optString(
                                "mime_type",
                                "image/png"
                            )

                        if (data.isNotBlank()) {

                            return ImageResult(
                                base64Data = data,
                                mimeType = mimeType
                            )
                        }
                    }

                    // Some response variants may
                    // put image information inside
                    // an output_image object.
                    val nestedImage =
                        item.optJSONObject(
                            "image"
                        )

                    if (nestedImage != null) {

                        val data =
                            nestedImage.optString(
                                "data",
                                ""
                            )

                        if (data.isNotBlank()) {

                            return ImageResult(
                                base64Data = data,
                                mimeType =
                                    nestedImage.optString(
                                        "mime_type",
                                        "image/png"
                                    )
                            )
                        }
                    }
                }
            }

            // ------------------------------------------------------------
            // LEGACY / CANDIDATE FORMAT
            // ------------------------------------------------------------

            val candidates =
                root.optJSONArray(
                    "candidates"
                )

            if (candidates != null) {

                for (
                    i in 0 until candidates.length()
                ) {

                    val candidate =
                        candidates.optJSONObject(i)
                            ?: continue

                    val content =
                        candidate.optJSONObject(
                            "content"
                        )
                            ?: continue

                    val parts =
                        content.optJSONArray(
                            "parts"
                        )
                            ?: continue

                    for (
                        j in 0 until parts.length()
                    ) {

                        val part =
                            parts.optJSONObject(j)
                                ?: continue

                        val inlineData =
                            part.optJSONObject(
                                "inlineData"
                            )
                                ?: continue

                        val data =
                            inlineData.optString(
                                "data",
                                ""
                            )

                        if (data.isNotBlank()) {

                            return ImageResult(
                                base64Data = data,
                                mimeType =
                                    inlineData.optString(
                                        "mimeType",
                                        "image/png"
                                    )
                            )
                        }
                    }
                }
            }

            null

        } catch (
            _: Exception
        ) {

            null
        }
    }

    // ========================================================================
    // ERROR PARSER
    // ========================================================================

    private fun extractErrorMessage(
        responseBody: String
    ): String? {

        if (responseBody.isBlank()) {
            return null
        }

        return try {

            val root =
                JSONObject(
                    responseBody
                )

            val error =
                root.optJSONObject(
                    "error"
                )

            error?.optString(
                "message"
            )?.takeIf {
                it.isNotBlank()
            }

        } catch (
            _: Exception
        ) {

            null
        }
    }

    // ========================================================================
    // FRIENDLY ERRORS
    // ========================================================================

    private fun friendlyError(
        statusCode: Int
    ): String {

        return when (statusCode) {

            400 ->
                "Invalid image request."

            401, 403 ->
                "Gemini API authentication failed."

            404 ->
                "Gemini image model is unavailable."

            429 ->
                "Image generation is temporarily busy. Please try again shortly."

            500, 502, 503, 504 ->
                "Image generation service is temporarily unavailable. Please try again shortly."

            else ->
                "Image generation failed. Please try again."
        }
    }

    // ========================================================================
    // MIME TYPE
    // ========================================================================

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

    // ========================================================================
    // BITMAP -> BASE64
    // ========================================================================

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
            quality.coerceIn(
                1,
                100
            ),
            stream
        )

        Base64.encodeToString(
            stream.toByteArray(),
            Base64.NO_WRAP
        )
    }
}

// ============================================================================
// IMAGE GENERATION EXCEPTION
// ============================================================================

class ImageGenerationException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null
) : Exception(
    message,
    cause
)
