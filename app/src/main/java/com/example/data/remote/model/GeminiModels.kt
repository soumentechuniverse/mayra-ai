package com.example.data.remote.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val tools: List<JsonObject>? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val parts: List<Part>,
    val role: String? = null
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val thinkingConfig: ThinkingConfig? = null
)

@Serializable
data class ThinkingConfig(
    val thinkingLevel: String? = null,
    val thinkingBudget: Int? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null,
    val usageMetadata: UsageMetadata? = null
)

@Serializable
data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
    val groundingMetadata: GroundingMetadata? = null
)

@Serializable
data class GroundingMetadata(
    val webSearchQueries: List<String>? = null,
    val groundingChunks: List<GroundingChunk>? = null
)

@Serializable
data class GroundingChunk(
    val web: WebChunk? = null
)

@Serializable
data class WebChunk(
    val uri: String? = null,
    val title: String? = null
)

@Serializable
data class UsageMetadata(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val totalTokenCount: Int? = null
)

@Serializable
data class WebSourceCitation(
    val title: String,
    val url: String
)
