package com.example.data.service

import com.example.domain.model.AiModelConfig
import com.example.domain.model.AiStreamChunk
import com.example.domain.model.Attachment
import com.example.domain.model.ChatMessage
import com.example.domain.service.AiService
import kotlinx.coroutines.flow.Flow

/**
 * Compatibility wrapper.
 *
 * Gemini is no longer used by Mayra AI.
 * This class keeps the existing GeminiService name so older
 * parts of the app can continue working while using OpenAI.
 */
class GeminiService(
    private val openAIService: OpenAIService = OpenAIService()
) : AiService {

    override suspend fun isAvailable(): Boolean {
        return openAIService.isAvailable()
    }

    override suspend fun generateResponse(
        conversationId: String,
        prompt: String,
        history: List<ChatMessage>,
        config: AiModelConfig,
        attachments: List<Attachment>,
        enableSearch: Boolean
    ): Result<String> {

        return openAIService.generateResponse(
            conversationId = conversationId,
            prompt = prompt,
            history = history,
            config = config,
            attachments = attachments,
            enableSearch = enableSearch
        )
    }

    override fun generateStream(
        conversationId: String,
        prompt: String,
        history: List<ChatMessage>,
        config: AiModelConfig,
        attachments: List<Attachment>,
        enableSearch: Boolean
    ): Flow<AiStreamChunk> {

        return openAIService.generateStream(
            conversationId = conversationId,
            prompt = prompt,
            history = history,
            config = config,
            attachments = attachments,
            enableSearch = enableSearch
        )
    }
}

/**
 * Kept for source compatibility with older code/tests.
 * Gemini API is no longer used.
 */
class GeminiApiException(
    val statusCode: Int,
    message: String,
    val retryAfterSeconds: Long? = null
) : Exception(message)
