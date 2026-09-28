package com.example

import com.example.data.service.GeminiApiException
import com.example.data.service.GeminiService
import com.example.domain.model.AiModelConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiServiceTest {

    @Test
    fun `default GeminiService constructor initializes correctly`() {
        val service = GeminiService()
        assertNotNull(service)
    }

    @Test
    fun `isAvailable handles quoted key strings from secrets or environment`() = runBlocking {
        val service = GeminiService(apiKeyProvider = { "\"AIzaSyQuotedKey123\"" })
        assertTrue(service.isAvailable())
    }

    @Test
    fun `isAvailable returns false for placeholder key`() = runBlocking {
        val service = GeminiService(apiKeyProvider = { "MY_GEMINI_API_KEY" })
        assertFalse(service.isAvailable())
    }

    @Test
    fun `isAvailable returns true for valid key`() = runBlocking {
        val service = GeminiService(apiKeyProvider = { "AIzaSyFakeKeyTest123" })
        assertTrue(service.isAvailable())
    }

    @Test
    fun `generateStream throws IllegalStateException when key is missing`() = runBlocking {
        val service = GeminiService(apiKeyProvider = { "MY_GEMINI_API_KEY" })
        try {
            service.generateStream("conv-1", "Hello", emptyList(), AiModelConfig()).toList()
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Gemini API key is not configured") == true)
        }
    }

    @Test
    fun `generateStream streams chunks from sse response correctly`() = runBlocking {
        val sseData = """
            data: {"candidates": [{"content": {"parts": [{"text": "Hello"}], "role": "model"}}]}

            data: {"candidates": [{"content": {"parts": [{"text": " from Mayra!"}], "role": "model"}}]}

            data: [DONE]
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(sseData.toResponseBody("text/event-stream".toMediaType()))
                    .build()
            })
            .build()

        val service = GeminiService(
            apiKeyProvider = { "AIzaSyFakeKeyTest123" },
            okHttpClient = mockClient
        )

        val chunks = service.generateStream("conv-1", "Hi", emptyList(), AiModelConfig()).toList()

        assertEquals(3, chunks.size)
        assertEquals("Hello", chunks[0].textDelta)
        assertFalse(chunks[0].isComplete)
        assertEquals(" from Mayra!", chunks[1].textDelta)
        assertFalse(chunks[1].isComplete)
        assertEquals("", chunks[2].textDelta)
        assertTrue(chunks[2].isComplete)
    }

    @Test
    fun `generateStream throws GeminiApiException with clean message on HTTP 401`() = runBlocking {
        val errorJson = """{"error": {"code": 401, "message": "API key not valid"}}"""
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body(errorJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val service = GeminiService(
            apiKeyProvider = { "AIzaSyFakeKeyTest123" },
            okHttpClient = mockClient
        )

        try {
            service.generateStream("conv-1", "Hi", emptyList(), AiModelConfig()).toList()
            fail("Expected GeminiApiException")
        } catch (e: GeminiApiException) {
            assertEquals(401, e.statusCode)
            assertTrue(e.message?.contains("Authentication failed") == true)
            assertFalse(e.message?.contains("AIzaSyFakeKeyTest123") == true)
        }
    }
}
