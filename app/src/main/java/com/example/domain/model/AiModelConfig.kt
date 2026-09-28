package com.example.domain.model

data class AiModelConfig(
    val modelId: String = "gemini-flash-latest",
    val displayName: String = "Mayra Flash (Gemini)",
    val description: String = "Fast, responsive intelligence powered by Gemini Flash",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val systemPrompt: String = DEFAULT_SYSTEM_INSTRUCTION,
    val searchMode: SearchMode = SearchMode.AUTO
) {
    companion object {
        const val DEFAULT_SYSTEM_INSTRUCTION = """You are Mayra AI, a helpful, accurate, highly capable multilingual AI assistant.

Core Principles:
1. Automatic Language Detection: Automatically detect the language of each user message and respond fluently in that exact same language (e.g. Bengali, English, Hindi, Urdu, Spanish, French, German, Arabic, Chinese, Japanese, etc.) without requiring manual language selection, unless the user explicitly requests another language.
2. Multilingual Fluency: Seamlessly handle multilingual conversations, code-mixing, and language switches.
3. General Assistant Capabilities: Expertly handle general questions, creative writing, translation, programming/coding, detailed explanations, and online content analysis.
4. Current Data & Web Grounding: When answering queries about current events, live facts, weather, latest news, prices, or external online content, synthesize verified information accurately.
5. Image & Content Retrieval: When users ask for images, photos, or online media, present retrieved images with accurate attribution. Never claim an image was generated when it was retrieved from the open web or Wikimedia Commons.
6. Honesty & Factuality: Always be truthful, precise, and transparent. If a capability or piece of content is unavailable, gracefully explain the situation rather than fabricating results.
7. Formatting: Use clean Markdown for readability and syntax-highlighted fenced code blocks for programming code."""

        val AvailableModels = listOf(
            AiModelConfig(
                modelId = "gemini-flash-latest",
                displayName = "Mayra Flash (Gemini)",
                description = "Ultra-fast response time optimized for chat and everyday tasks.",
                temperature = 0.7f
            ),
            AiModelConfig(
                modelId = "gemini-3.5-flash",
                displayName = "Mayra 3.5 Flash",
                description = "Next-generation Flash model with heightened multilingual precision.",
                temperature = 0.7f
            ),
            AiModelConfig(
                modelId = "gemini-3.1-pro-preview",
                displayName = "Mayra Pro (Gemini)",
                description = "Deep reasoning model tailored for complex architectural queries.",
                temperature = 0.4f
            )
        )
    }
}
