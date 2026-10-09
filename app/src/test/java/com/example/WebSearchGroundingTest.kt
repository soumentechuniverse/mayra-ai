package com.example

import com.example.data.local.entity.ChatMessageEntity
import com.example.data.service.DefaultSearchIntentDetector
import com.example.domain.model.ChatMessage
import com.example.domain.model.MessageRole
import com.example.domain.model.SearchMode
import com.example.domain.model.SearchSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
        val message = ChatMessage(
            conversationId = "conv-1",
            role = MessageRole.ASSISTANT,
            content = "Here is the latest news.",
            searchSources = sources
        )

        val entity = ChatMessageEntity.fromDomain(message)
        assertNotNull(entity.searchSourcesJson)

        val restored = entity.toDomain()
        assertEquals(2, restored.searchSources.size)
        assertEquals("BBC News", restored.searchSources[0].title)
        assertEquals("https://www.bbc.com/news", restored.searchSources[0].url)
        assertEquals("bbc.com", restored.searchSources[0].domain)
        assertEquals("Android Dev", restored.searchSources[1].title)
    }

    @Test
    fun `intent detector triggers on dynamic temporal queries in English and Bengali`() = runBlocking {
        assertTrue(detector.detect("What is today's weather?").needsSearch)
        assertTrue(detector.detect("What happened today in world news?").needsSearch)
        assertTrue(detector.detect("What is the current price of gold?").needsSearch)
        assertTrue(detector.detect("What are the latest technology news?").needsSearch)
        assertTrue(detector.detect("আজকের আবহাওয়া কেমন?").needsSearch)
        assertTrue(detector.detect("সর্বশেষ প্রযুক্তি সংবাদ কী?").needsSearch)
        assertTrue(detector.detect("সোনার বর্তমান দাম কত?").needsSearch)
    }

    @Test
    fun `intent detector handles explicit search and mode overrides`() = runBlocking {
        val explicit = detector.detect("Search the web for the new AI model")
        assertTrue(explicit.needsSearch)
        assertTrue(explicit.isExplicitRequest)

        assertFalse(
            detector.detect("latest breaking news", mode = SearchMode.DISABLED).needsSearch
        )
        assertTrue(
            detector.detect("hello world", mode = SearchMode.ENABLED).needsSearch
        )
    }

    @Test
    fun `intent detector does not search for evergreen or creative requests`() = runBlocking {
        assertFalse(detector.detect("What is 25 * 4?").needsSearch)
        assertFalse(detector.detect("Write a poem about autumn leaves").needsSearch)
        assertFalse(detector.detect("একটি সুন্দর কবিতা লেখো").needsSearch)
        assertFalse(detector.detect("How does photosynthesis work?").needsSearch)
        assertFalse(
            detector.detect(
                "Look at this chart and explain it",
                hasAttachments = true
            ).needsSearch
        )
        assertTrue(
            detector.detect(
                "Search the web for details regarding this document",
                hasAttachments = true
            ).needsSearch
        )
    }
}
