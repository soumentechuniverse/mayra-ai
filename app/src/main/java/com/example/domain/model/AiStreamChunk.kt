package com.example.domain.model

data class AiStreamChunk(
    val conversationId: String,
    val textDelta: String,
    val isComplete: Boolean = false,
    val searchQueries: List<String> = emptyList(),
    val searchSources: List<SearchSource> = emptyList(),
    val isSearching: Boolean = false
)
