package com.example.data.service

import com.example.domain.model.SearchDecision
import com.example.domain.model.SearchMode
import com.example.domain.service.SearchIntentDetector
import com.example.domain.service.WebSearchService

/**
 * Concrete implementation of [WebSearchService] backing Google Search grounding for Gemini models.
 */
class GeminiWebSearchService(
    private val intentDetector: SearchIntentDetector = DefaultSearchIntentDetector()
) : WebSearchService {

    companion object {
        private val SUPPORTED_GROUNDING_MODELS = setOf(
            "gemini-flash-latest",
            "gemini-3.5-flash",
            "gemini-3.1-pro-preview",
            "gemini-3.1-flash-lite-preview"
        )
    }

    override fun isSearchSupported(modelId: String): Boolean {
        if (SUPPORTED_GROUNDING_MODELS.contains(modelId)) return true
        // Generally supported on modern Gemini 2.x and 3.x models
        return modelId.startsWith("gemini-")
    }

    override suspend fun evaluateSearchDecision(
        query: String,
        mode: SearchMode,
        hasAttachments: Boolean
    ): SearchDecision {
        return intentDetector.detect(query, mode, hasAttachments)
    }
}
