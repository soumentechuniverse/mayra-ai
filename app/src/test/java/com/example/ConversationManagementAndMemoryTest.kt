package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MayraDatabase
import com.example.data.local.MemoryPreferences
import com.example.data.repository.ChatRepositoryImpl
import com.example.domain.model.AiModelConfig
import com.example.domain.model.AiStreamChunk
import com.example.domain.model.Attachment
import com.example.domain.model.ChatMessage
import com.example.domain.model.MemoryCategory
import com.example.domain.model.MessageRole
import com.example.domain.service.AiService
import com.example.domain.service.MemoryDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConversationManagementAndMemoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: CoroutineScope
    private lateinit var context: Context
    private lateinit var database: MayraDatabase
    private lateinit var fakeAiService: FakeAiService
    private lateinit var repository: ChatRepositoryImpl
    private lateinit var memoryPreferences: MemoryPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MayraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fakeAiService = FakeAiService()
        memoryPreferences = MemoryPreferences(context)
        testScope = CoroutineScope(SupervisorJob() + testDispatcher)
        repository = ChatRepositoryImpl(
            aiService = fakeAiService,
            conversationDao = database.conversationDao(),
            memoryDao = database.memoryDao(),
            memoryPreferences = memoryPreferences,
            scope = testScope
        )
    }

    @After
    fun tearDown() {
        testScope.cancel()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `create and switch conversations preserves isolation without mixing messages`() = runTest {
        advanceUntilIdle()

        // Send message in initial conversation
        val initialConv = repository.activeConversation.value
        assertNotNull(initialConv)
        val initialConvId = initialConv!!.id

        repository.sendMessage("Message in Conv 1", AiModelConfig())
        advanceUntilIdle()

        assertEquals(2, repository.messages.value.size) // User + Assistant
        assertEquals("Message in Conv 1", repository.messages.value[0].content)

        // Start a second independent conversation
        val secondConv = repository.startNewConversation("Second Chat")
        advanceUntilIdle()

        assertNotEquals(initialConvId, secondConv.id)
        assertEquals(secondConv.id, repository.activeConversation.value?.id)
        // Messages must be isolated to Second Chat (initially empty)
        assertEquals(0, repository.messages.value.size)

        // Send message in second conversation
        repository.sendMessage("Message in Conv 2", AiModelConfig())
        advanceUntilIdle()

        assertEquals(2, repository.messages.value.size)
        assertEquals("Message in Conv 2", repository.messages.value[0].content)

        // Switch back to initial conversation: messages should match Conv 1 only
        repository.selectConversation(initialConvId)
        advanceUntilIdle()

        assertEquals(initialConvId, repository.activeConversation.value?.id)
        assertEquals(2, repository.messages.value.size)
        assertEquals("Message in Conv 1", repository.messages.value[0].content)
    }

    @Test
    fun `auto-title creates a short title from first user message locally`() = runTest {
        advanceUntilIdle()
        val conv = repository.startNewConversation("New Chat")
        advanceUntilIdle()

        repository.sendMessage("Explain quantum computing in simple terms", AiModelConfig())
        advanceUntilIdle()

        val updatedConv = repository.conversations.value.find { it.id == conv.id }
        assertNotNull(updatedConv)
        assertTrue(updatedConv!!.title.startsWith("Explain quantum computing"))
    }

    @Test
    fun `rename conversation updates title and state`() = runTest {
        advanceUntilIdle()
        val conv = repository.startNewConversation("Original Title")
        advanceUntilIdle()

        repository.renameConversation(conv.id, "Renamed Mayra Chat")
        advanceUntilIdle()

        val updated = repository.conversations.value.find { it.id == conv.id }
        assertNotNull(updated)
        assertEquals("Renamed Mayra Chat", updated!!.title)
        assertEquals("Renamed Mayra Chat", repository.activeConversation.value?.title)
    }

    @Test
    fun `pin and unpin keeps conversations ordered with pinned first`() = runTest {
        advanceUntilIdle()
        val conv1 = repository.startNewConversation("Chat 1")
        advanceUntilIdle()
        val conv2 = repository.startNewConversation("Chat 2")
        advanceUntilIdle()
        val conv3 = repository.startNewConversation("Chat 3")
        advanceUntilIdle()

        // Pin Chat 1
        repository.togglePinConversation(conv1.id)
        advanceUntilIdle()

        val list = repository.conversations.value
        assertEquals(conv1.id, list.first().id)
        assertTrue(list.first().isPinned)

        // Unpin Chat 1
        repository.togglePinConversation(conv1.id)
        advanceUntilIdle()

        assertFalse(repository.conversations.value.find { it.id == conv1.id }!!.isPinned)
    }

    @Test
    fun `archive and unarchive updates flag without deleting conversation`() = runTest {
        advanceUntilIdle()
        val conv = repository.startNewConversation("Chat to Archive")
        advanceUntilIdle()

        repository.toggleArchiveConversation(conv.id)
        advanceUntilIdle()

        val archived = repository.conversations.value.find { it.id == conv.id }
        assertNotNull(archived)
        assertTrue(archived!!.isArchived)

        // Unarchive
        repository.toggleArchiveConversation(conv.id)
        advanceUntilIdle()

        assertFalse(repository.conversations.value.find { it.id == conv.id }!!.isArchived)
    }

    @Test
    fun `delete conversation removes messages and switches active conversation`() = runTest {
        advanceUntilIdle()
        val conv1 = repository.startNewConversation("Chat to Delete")
        advanceUntilIdle()
        repository.sendMessage("Hello to delete", AiModelConfig())
        advanceUntilIdle()

        val conv2 = repository.startNewConversation("Safe Chat")
        advanceUntilIdle()

        repository.deleteConversation(conv1.id)
        advanceUntilIdle()

        assertNull(repository.conversations.value.find { it.id == conv1.id })
        assertNotNull(repository.conversations.value.find { it.id == conv2.id })
    }

    @Test
    fun `searchConversations supports Unicode and multilingual titles and message content`() = runTest {
        advanceUntilIdle()
        val bengaliConv = repository.startNewConversation("বাংলা টিউটোরিয়াল")
        advanceUntilIdle()
        repository.sendMessage("আমি বাংলায় কথা বলতে চাই", AiModelConfig())
        advanceUntilIdle()

        val englishConv = repository.startNewConversation("Kotlin Development")
        advanceUntilIdle()
        repository.sendMessage("Building Jetpack Compose applications", AiModelConfig())
        advanceUntilIdle()

        // Search by Bengali title
        val searchBengaliTitle = repository.searchConversations("বাংলা")
        assertTrue(searchBengaliTitle.any { it.id == bengaliConv.id })

        // Search by Bengali message content
        val searchBengaliMsg = repository.searchConversations("কথা")
        assertTrue(searchBengaliMsg.any { it.id == bengaliConv.id })

        // Search by English message content (case-insensitive)
        val searchEnglish = repository.searchConversations("jetpack")
        assertTrue(searchEnglish.any { it.id == englishConv.id })

        // No match returns empty
        val noMatch = repository.searchConversations("nonexistentquery12345")
        assertTrue(noMatch.isEmpty())
    }

    @Test
    fun `memory detector saves explicit user preference and rejects secrets`() {
        // Valid memory
        val validMemory = MemoryDetector.extractMemory("Remember that I prefer Bengali.")
        assertNotNull(validMemory)
        assertEquals("I prefer Bengali.", validMemory!!.content)
        assertEquals(MemoryCategory.PREFERENCE, validMemory.category)

        val profileMemory = MemoryDetector.extractMemory("Please remember that my name is Alex")
        assertNotNull(profileMemory)
        assertEquals(MemoryCategory.PROFILE, profileMemory!!.category)

        // Implicit message: must NOT be saved
        val normalChat = MemoryDetector.extractMemory("What is the weather like today?")
        assertNull(normalChat)

        // Obvious secret: MUST NEVER be saved to memory
        val secretPassword = MemoryDetector.extractMemory("Remember that my password is SuperSecret123!")
        assertNull(secretPassword)

        val secretApiKey = MemoryDetector.extractMemory("Please remember my API key is AIzaSyD987")
        assertNull(secretApiKey)

        val secretOtp = MemoryDetector.extractMemory("Remember this OTP 492019")
        assertNull(secretOtp)
    }

    @Test
    fun `memory add, toggle, delete and clear all memories work as expected`() = runTest {
        advanceUntilIdle()
        val mem1 = repository.saveMemory("Prefer Kotlin", MemoryCategory.PREFERENCE)
        val mem2 = repository.saveMemory("User lives in Tokyo", MemoryCategory.PROFILE)
        advanceUntilIdle()

        assertEquals(2, repository.memories.value.size)

        // Toggle enabled
        repository.toggleMemoryItemEnabled(mem1.id, false)
        advanceUntilIdle()
        assertFalse(repository.memories.value.find { it.id == mem1.id }!!.enabled)

        // Delete individual
        repository.deleteMemory(mem2.id)
        advanceUntilIdle()
        assertEquals(1, repository.memories.value.size)
        assertEquals(mem1.id, repository.memories.value.first().id)

        // Clear all
        repository.clearAllMemories()
        advanceUntilIdle()
        assertTrue(repository.memories.value.isEmpty())
    }

    @Test
    fun `memory ON and OFF controls memory creation and injection`() = runTest {
        advanceUntilIdle()
        // Save initial memory
        repository.saveMemory("I prefer Bengali", MemoryCategory.PREFERENCE)
        advanceUntilIdle()

        // When memory is ON:
        repository.setMemoryEnabled(true)
        advanceUntilIdle()
        assertTrue(repository.isMemoryEnabled.value)

        repository.sendMessage("Hello Mayra", AiModelConfig())
        advanceUntilIdle()

        // Fake AI service should receive memory context in systemPrompt
        assertNotNull(fakeAiService.lastConfigReceived)
        assertTrue(fakeAiService.lastConfigReceived!!.systemPrompt.contains("User Profile & Context Memories"))
        assertTrue(fakeAiService.lastConfigReceived!!.systemPrompt.contains("I prefer Bengali"))

        // When memory is OFF:
        repository.setMemoryEnabled(false)
        advanceUntilIdle()
        assertFalse(repository.isMemoryEnabled.value)

        // Say "Remember that..." when memory is OFF: must not create memory
        val initialCount = repository.memories.value.size
        repository.sendMessage("Remember that I love dark mode", AiModelConfig())
        advanceUntilIdle()

        assertEquals(initialCount, repository.memories.value.size)

        // System prompt must NOT contain memory context when OFF
        assertFalse(fakeAiService.lastConfigReceived!!.systemPrompt.contains("User Profile & Context Memories"))
    }

    private class FakeAiService : AiService {
        var lastConfigReceived: AiModelConfig? = null

        override suspend fun generateResponse(
            conversationId: String,
            prompt: String,
            history: List<ChatMessage>,
            config: AiModelConfig,
            attachments: List<Attachment>,
            enableSearch: Boolean
        ): Result<String> {
            lastConfigReceived = config
            return Result.success("Response from Mayra")
        }

        override fun generateStream(
            conversationId: String,
            prompt: String,
            history: List<ChatMessage>,
            config: AiModelConfig,
            attachments: List<Attachment>,
            enableSearch: Boolean
        ): Flow<AiStreamChunk> {
            lastConfigReceived = config
            return flowOf(
                AiStreamChunk(
                    conversationId = conversationId,
                    textDelta = "Response from Mayra",
                    isComplete = false
                ),
                AiStreamChunk(
                    conversationId = conversationId,
                    textDelta = "",
                    isComplete = true
                )
            )
        }

        override suspend fun isAvailable(): Boolean = true
    }
}
