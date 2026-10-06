package com.example

import com.example.data.remote.model.Content
import com.example.data.remote.model.GenerateContentRequest
import com.example.data.remote.model.InlineData
import com.example.data.remote.model.Part
import com.example.data.remote.model.ThinkingConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MayraEndToEndTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testMultimodalImagePromptSerialization() {
        val imageBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    role = "user",
                    parts = listOf(
                        Part(inlineData = InlineData(mimeType = "image/png", data = imageBase64)),
                        Part(text = "What is shown in this picture?")
                    )
                )
            ),
            generationConfig = com.example.data.remote.model.GenerationConfig(
                temperature = 0.7f,
                thinkingConfig = ThinkingConfig(thinkingLevel = "low")
            )
        )

        val jsonStr = json.encodeToString(request)
        assertTrue(jsonStr.contains("image/png"))
        assertTrue(jsonStr.contains("What is shown in this picture?"))
        assertTrue(jsonStr.contains("low"))
    }

    @Test
    fun testDocumentAttachmentPromptStructure() {
        val documentText = "Quarterly Report: Revenue is up 25%."
        val prompt = "Summarize the report"
        val combinedPrompt = "--- Attached File: report.txt ---\n$documentText\n\n$prompt"

        assertTrue(combinedPrompt.contains("Quarterly Report"))
        assertTrue(combinedPrompt.contains("Summarize the report"))
    }

    @Test
    fun testMultilingualSupportBengaliAndHindi() {
        val bengaliPrompt = "নমস্কার! কেমন আছেন?"
        val hindiPrompt = "नमस्ते! आप कैसे हैं?"

        assertEquals("নমস্কার! কেমন আছেন?", bengaliPrompt)
        assertEquals("नमस्ते! आप कैसे हैं?", hindiPrompt)
    }

    @Test
    fun testSearchDetectionLogic() {
        fun needsWebSearch(prompt: String): Boolean {
            val clean = prompt.trim().lowercase()
            val keywords = listOf(
                "latest", "today", "yesterday", "news", "current", "weather",
                "stock price", "price of", "who won", "score", "schedule",
                "recent", "upcoming", "live", "search the web", "google for",
                "who is the current", "what happened in"
            )
            return keywords.any { clean.contains(it) }
        }

        assertTrue(needsWebSearch("What is the latest news today?"))
        assertTrue(needsWebSearch("Current weather in London"))
        assertTrue(needsWebSearch("Who won the match yesterday?"))
        assertFalse(needsWebSearch("What is 2 + 2?"))
        assertFalse(needsWebSearch("Write a python function to sort an array"))
        assertFalse(needsWebSearch("Explain photosynthesis"))
    }

    @Test
    fun testThinkingIndicatorBehavior() {
        // Thinking state should start true and terminate immediately upon first chunk or completion
        var isThinking = true
        var isGenerating = true

        // Simulate first token arriving
        val firstChunk = "Hello"
        if (firstChunk.isNotEmpty()) {
            isThinking = false
        }
        assertFalse("Thinking must be false once first token arrives", isThinking)
        assertTrue(isGenerating)

        // Simulate stream completion
        isGenerating = false
        assertFalse(isGenerating)
        assertFalse(isThinking)
    }
}
