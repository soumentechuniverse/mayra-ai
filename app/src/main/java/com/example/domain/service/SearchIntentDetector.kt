package com.example.domain.service

import com.example.domain.model.SearchDecision
import com.example.domain.model.SearchMode

interface SearchIntentDetector {
    /**
     * Evaluates a user prompt and decides whether it requires real-time web search grounding.
     *
     * @param query The user's input message text
     * @param mode Current search mode preference (AUTO, ENABLED, DISABLED)
     * @param hasAttachments Whether attachments (images, PDFs, documents) are present
     * @return [SearchDecision] indicating if web search should be executed and why
     */
    suspend fun detect(
        query: String,
        mode: SearchMode = SearchMode.AUTO,
        hasAttachments: Boolean = false
    ): SearchDecision
}
