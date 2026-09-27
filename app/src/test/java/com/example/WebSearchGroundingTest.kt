package com.example

import com.example.data.local.entity.ChatMessageEntity
import com.example.data.service.DefaultSearchIntentDetector
import com.example.data.service.GeminiService
import com.example.domain.model.AiModelConfig
import com.example.domain.model.ChatMessage
import com.example.domain.model.MessageRole
import com.example.domain.model.SearchMode
import com.example.domain.model.SearchSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WebSearchGroundingTest {

    private val detector = DefaultSearchIntentDetector()

    @Test
    fun `search source extracts domain correctly`() {
        assertEquals("bbc.com", SearchSource.extractDomain("https://www.bbc.com/news/technology-12345"))
        assertEquals("reuters.com", SearchSource.extractDomain("http://reuters.com/article/1"))
        assertEquals("developer.android.com", SearchSource.extractDomain("https://developer.android.com/jetpack/compose"))
        assertEquals("weather.com", SearchSource.extractDomain("https://weather.com:443/today"))
    }

    @Test
    fun `chat message entity serializes and restores search sources`() {
        val sources = listOf(
            SearchSource(title = "BBC News", url = "https://www.bbc.com/news", domain = "bbc.com"),
            SearchSource(title = "Android Dev", url = "https://developer.android.com", domain = "developer.android.com")
        )
        val msg = ChatMessage(
            conversationId = "conv-1",
            role = MessageRole.ASSISTANT,
            content = "Here is the latest news.",
            searchSources = sources
        )

        val entity = ChatMessageEntity.fromDomain(msg)
        assertNotNull(entity.searchSourcesJson)

        val restored = entity.toDomain()
        assertEquals(2, restored.searchSources.size)
        assertEquals("BBC News", restored.searchSources[0].title)
        assertEquals("https://www.bbc.com/news", restored.searchSources[0].url)
        assertEquals("bbc.com", restored.searchSources[0].domain)
        assertEquals("Android Dev", restored.searchSources[1].title)
    }

    @Test
    fun `intent detector triggers on dynamic temporal queries in English`() = runBlocking {
        val q1 = detector.detect("What is today's weather?")
        assertTrue("Expected search for today's weather", q1.needsSearch)

        val q2 = detector.detect("What happened today in world news?")
        assertTrue("Expected search for news today", q2.needsSearch)

        val q3 = detector.detect("What is the current price of gold?")
        assertTrue("Expected search for current price of gold", q3.needsSearch)

        val q4 = detector.detect("What are the latest technology news?")
        assertTrue("Expected search for latest tech news", q4.needsSearch)

        val q5 = detector.detect("What is the latest Android version?")
        assertTrue("Expected search for latest Android version", q5.needsSearch)
    }

    @Test
    fun `intent detector triggers on dynamic queries in Bengali`() = runBlocking {
        val q1 = detector.detect("আজকের আবহাওয়া কেমন?")
        assertTrue("Expected search for Bengali weather query", q1.needsSearch)

        val q2 = detector.detect("সর্বশেষ প্রযুক্তি সংবাদ কী?")
        assertTrue("Expected search for Bengali latest tech news", q2.needsSearch)

        val q3 = detector.detect("সোনার বর্তমান দাম কত?")
        assertTrue("Expected search for Bengali current gold price", q3.needsSearch)
    }

    @Test
    fun `intent detector triggers on explicit search requests`() = runBlocking {
        val q1 = detector.detect("Search the web for the new Gemini 3.5 model")
        assertTrue(q1.needsSearch)
        assertTrue(q1.isExplicitRequest)

        val q2 = detector.detect("ওয়েবে খুঁজে দেখো সাম্প্রতিক বিজ্ঞান আবিষ্কার")
        assertTrue(q2.needsSearch)
        assertTrue(q2.isExplicitRequest)

        val q3 = detector.detect("Find online the release date of GTA 6")
        assertTrue(q3.needsSearch)
        assertTrue(q3.isExplicitRequest)
    }

    @Test
    fun `intent detector rejects non-search queries like math, writing, and evergreen concepts`() = runBlocking {
        // Basic mathematics
        val math = detector.detect("What is 25 * 4?")
        assertFalse("Math should not trigger search", math.needsSearch)

        val calc = detector.detect("50 + 20 * 3")
        assertFalse("Calculation should not trigger search", calc.needsSearch)

        // Creative writing
        val writing = detector.detect("Write a poem about the autumn leaves")
        assertFalse("Creative writing should not trigger search", writing.needsSearch)

        val bengaliWriting = detector.detect("একটি সুন্দর কবিতা লেখো")
        assertFalse("Bengali poem should not trigger search", bengaliWriting.needsSearch)

        // Translation
        val translation = detector.detect("Translate this sentence to French: Hello how are you?")
        assertFalse("Translation should not trigger search", translation.needsSearch)

        // Evergreen concepts
        val science = detector.detect("How does photosynthesis work?")
        assertFalse("Evergreen science should not trigger search", science.needsSearch)

        val coding = detector.detect("How does recursion work in programming?")
        assertFalse("Evergreen coding should not trigger search", coding.needsSearch)
    }

    @Test
    fun `intent detector preserves privacy when attachments are present`() = runBlocking {
        // Attachments without explicit search instructions must not search web
        val decision = detector.detect("Look at this chart and explain it", hasAttachments = true)
        assertFalse("Attachments should not automatically trigger search", decision.needsSearch)

        // Attachments WITH explicit search instruction can search web
        val explicitWithAttachment = detector.detect(
            "Search the web for more details regarding this document",
            hasAttachments = true
        )
        assertTrue("Explicit search with attachment should allow search", explicitWithAttachment.needsSearch)
    }

    @Test
    fun `intent detector respects search mode overrides`() = runBlocking {
        // Disabled mode ignores search signals
        val disabled = detector.detect("latest breaking news", mode = SearchMode.DISABLED)
        assertFalse(disabled.needsSearch)

        // Enabled mode forces search even for math/creative
        val enabled = detector.detect("hello world", mode = SearchMode.ENABLED)
        assertTrue(enabled.needsSearch)
    }

    @Test
    fun `gemini service adds google_search tool when enableSearch is true`() = runBlocking {
        var capturedRequestBody: String? = null

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val buffer = okio.Buffer()
                request.body?.writeTo(buffer)
                capturedRequestBody = buffer.readUtf8()

                val sseData = """
                    data: {"candidates": [{"content": {"parts": [{"text": "Today in Dhaka it is sunny."}], "role": "model"}, "groundingMetadata": {"webSearchQueries": ["weather Dhaka today"], "groundingChunks": [{"web": {"uri": "https://weather.com/dhaka", "title": "Weather Dhaka"}}]}}]}

                    data: [DONE]
                """.trimIndent()

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(sseData.toResponseBody("text/event-stream".toMediaType()))
                    .build()
            })
            .build()

        val service = GeminiService(
            apiKeyProvider = { "AIzaSyFakeKey123" },
            okHttpClient = mockClient
        )

        val chunks = service.generateStream(
            conversationId = "conv-1",
            prompt = "What is today's weather in Dhaka?",
            history = emptyList(),
            config = AiModelConfig(),
            attachments = emptyList(),
            enableSearch = true
        ).toList()

        assertNotNull(capturedRequestBody)
        val json = JSONObject(capturedRequestBody!!)
        assertTrue("Request must have tools array", json.has("tools"))
        val tools = json.getJSONArray("tools")
        assertEquals(1, tools.length())
        assertTrue("Tool must contain google_search", tools.getJSONObject(0).has("google_search"))

        // Verify that sources from groundingMetadata were parsed
        val lastChunk = chunks.last { it.isComplete }
        assertEquals(1, lastChunk.searchSources.size)
        assertEquals("https://weather.com/dhaka", lastChunk.searchSources[0].url)
        assertEquals("weather.com", lastChunk.searchSources[0].domain)
    }

    @Test
    fun `gemini service does not add tools when enableSearch is false`() = runBlocking {
        var capturedRequestBody: String? = null

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val buffer = okio.Buffer()
                request.body?.writeTo(buffer)
                capturedRequestBody = buffer.readUtf8()

                val sseData = """
                    data: {"candidates": [{"content": {"parts": [{"text": "Photosynthesis is..."}], "role": "model"}}]}

                    data: [DONE]
                """.trimIndent()

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(sseData.toResponseBody("text/event-stream".toMediaType()))
                    .build()
            })
            .build()

        val service = GeminiService(
            apiKeyProvider = { "AIzaSyFakeKey123" },
            okHttpClient = mockClient
        )

        service.generateStream(
            conversationId = "conv-1",
            prompt = "Explain photosynthesis",
            history = emptyList(),
            config = AiModelConfig(),
            attachments = emptyList(),
            enableSearch = false
        ).toList()

        assertNotNull(capturedRequestBody)
        val json = JSONObject(capturedRequestBody!!)
        assertFalse("Request must not have tools array when search is disabled", json.has("tools"))
    }
}
