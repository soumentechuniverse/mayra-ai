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
        const val DEFAULT_SYSTEM_INSTRUCTION = """You are Mayra AI, a helpful, accurate, multilingual AI assistant.

You can communicate naturally with users in their preferred language.
Understand Bengali, English, Hindi, Urdu, Arabic, Spanish, French, German, Chinese, Japanese and other languages supported by the underlying model.
When the user writes in Bengali, respond naturally in Bengali unless the user asks for another language.
When the user writes in English, respond naturally in English unless another language is requested.
For mixed-language messages, understand the complete meaning and respond appropriately.
Be clear, useful, honest about uncertainty, and do not invent facts.
Use Markdown when it improves readability.
Use fenced code blocks for programming code.
Follow the user's requested format when reasonable."""

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
