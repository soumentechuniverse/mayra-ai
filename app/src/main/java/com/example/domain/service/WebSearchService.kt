package com.example.domain.service

import com.example.domain.model.SearchDecision
import com.example.domain.model.SearchMode

interface WebSearchService {
    /**
     * Determines whether Google Search grounding is supported for the given model ID.
     */
    fun isSearchSupported(modelId: String): Boolean

    /**
     * Evaluates search necessity using the underlying search intent architecture.
     */
    suspend fun evaluateSearchDecision(
        query: String,
        mode: SearchMode = SearchMode.AUTO,
        hasAttachments: Boolean = false
    ): SearchDecision
}
