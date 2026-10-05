package com.example.data.service

import com.example.domain.model.AiModelConfig
import com.example.domain.model.AiStreamChunk
import com.example.domain.model.Attachment
import com.example.domain.model.ChatMessage
import com.example.domain.service.AiService
import kotlinx.coroutines.flow.Flow

/**
 * Main production AI engine.
 *
 * This class delegates all real AI work to GeminiService.
 * No fake/simulated answers are generated here.
 */
class DefaultMayraAiEngine(
    private val geminiService: GeminiService = GeminiService()
) : AiService {

    override suspend fun isAvailable(): Boolean {
        return geminiService.isAvailable()
    }

    override suspend fun generateResponse(
        conversationId: String,
        prompt: String,
        history: List<ChatMessage>,
        config: AiModelConfig,
        attachments: List<Attachment>,
        enableSearch: Boolean
    ): Result<String> {
        return geminiService.generateResponse(
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
        return geminiService.generateStream(
            conversationId = conversationId,
            prompt = prompt,
            history = history,
            config = config,
            attachments = attachments,
            enableSearch = enableSearch
        )
    }
}
