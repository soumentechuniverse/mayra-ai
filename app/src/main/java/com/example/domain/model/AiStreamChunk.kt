package com.example.domain.model

data class AiStreamChunk(
    val conversationId: String,
    val textDelta: String,
    val isComplete: Boolean = false,
    val finishReason: String? = null,
    val searchSources: List<SearchSource> = emptyList(),
    val searchQueries: List<String> = emptyList(),
    val isSearching: Boolean = false
)
