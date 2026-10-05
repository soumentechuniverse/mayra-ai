package com.example.domain.model

data class AiModelConfig(
    val modelId: String = "gemini-flash-latest",
    val displayName: String = "Mayra Flash (Gemini)",
    val description: String = "Fast, responsive intelligence powered by Gemini Flash",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val systemPrompt: String = DEFAULT_SYSTEM_INSTRUCTION,
    val searchMode: SearchMode = SearchMode.AUTO
) {
    companion object {

        const val DEFAULT_SYSTEM_INSTRUCTION = """
You are Mayra AI, a fast, accurate, helpful multilingual AI assistant.

Rules:
1. Answer directly and naturally. Avoid unnecessary filler or repeating the question.
2. Detect the user's language automatically and reply in the same language.
3. For Bengali, use natural Indian/West Bengal Bengali vocabulary.
4. Give accurate, well-reasoned answers. Never invent facts.
5. Never guess the current date, time, weather, news, prices, or other live information. Use available system/web information when needed.
6. Use web grounding when current or latest information is required.
7. Use natural emojis only when appropriate.
8. For coding questions, provide clean production-quality code.
9. If information cannot be verified, say so briefly instead of making it up.
10. Keep normal answers concise unless the user asks for detail.
"""

        val AvailableModels = listOf(
            AiModelConfig(
                modelId = "gemini-flash-latest",
                displayName = "Mayra Flash (Gemini)",
                description = "Fast response model for everyday conversations.",
                temperature = 0.7f,
                maxTokens = 2048
            ),
            AiModelConfig(
                modelId = "gemini-3.5-flash",
                displayName = "Mayra 3.5 Flash",
                description = "Fast multilingual model.",
                temperature = 0.7f,
                maxTokens = 2048
            ),
            AiModelConfig(
                modelId = "gemini-3.1-pro-preview",
                displayName = "Mayra Pro (Gemini)",
                description = "Advanced model for complex tasks.",
                temperature = 0.4f,
                maxTokens = 4096
            )
        )
    }
}
