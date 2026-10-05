package com.example

import com.example.data.repository.ChatRepository
import com.example.domain.model.AiModelConfig
import com.example.domain.model.Attachment
import com.example.domain.model.AttachmentMetadata
import com.example.domain.model.AttachmentType
import com.example.domain.model.ChatMessage
import com.example.domain.model.Conversation
import com.example.domain.model.MemoryCategory
import com.example.domain.model.MemoryItem
import com.example.domain.model.MessageRole
import com.example.domain.model.MessageStatus
import com.example.domain.model.SearchPhase
import com.example.ui.viewmodel.ChatUiEvent
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeChatRepository
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeChatRepository()
        viewModel = ChatViewModel(repository = fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `attachments can be added, updated, and removed in viewModel state`() = runTest(testDispatcher) {
        advanceUntilIdle()

        val att1 = Attachment(
            metadata = AttachmentMetadata(
                id = "att-1",
                name = "photo.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 1024,
                type = AttachmentType.IMAGE
            ),
            base64Data = "fakeImageData"
        )
        val att2 = Attachment(
            metadata = AttachmentMetadata(
                id = "att-2",
                name = "notes.txt",
                mimeType = "text/plain",
                sizeBytes = 500,
                type = AttachmentType.DOCUMENT
            ),
            textContent = "Meeting notes"
        )

        viewModel.onEvent(ChatUiEvent.AttachmentsSelected(listOf(att1, att2)))
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.pendingAttachments.size)
        assertEquals("photo.jpg", viewModel.uiState.value.pendingAttachments[0].metadata.name)
        assertEquals("notes.txt", viewModel.uiState.value.pendingAttachments[1].metadata.name)

        // Remove first attachment
        viewModel.onEvent(ChatUiEvent.RemovePendingAttachment("att-1"))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.pendingAttachments.size)
        assertEquals("att-2", viewModel.uiState.value.pendingAttachments[0].metadata.id)

        // Remove remaining attachment
        viewModel.onEvent(ChatUiEvent.RemovePendingAttachment("att-2"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pendingAttachments.isEmpty())
    }

    @Test
    fun `sending message includes pending attachments and clears composer state`() = runTest(testDispatcher) {
        advanceUntilIdle()

        val att = Attachment(
            metadata = AttachmentMetadata(
                id = "att-img",
                name = "screen.png",
                mimeType = "image/png",
                sizeBytes = 2048,
                type = AttachmentType.IMAGE
            ),
            base64Data = "base64PngData"
        )

        viewModel.onEvent(ChatUiEvent.InputTextChanged("Analyze this screenshot"))
        viewModel.onEvent(ChatUiEvent.AttachmentsSelected(listOf(att)))
        advanceUntilIdle()

        assertEquals("Analyze this screenshot", viewModel.uiState.value.inputText)
        assertEquals(1, viewModel.uiState.value.pendingAttachments.size)

        // Trigger send
        viewModel.onEvent(ChatUiEvent.SendClicked)
        advanceUntilIdle()

        // Composer input and attachments must be cleared immediately
        assertEquals("", viewModel.uiState.value.inputText)
        assertTrue(viewModel.uiState.value.pendingAttachments.isEmpty())

        // Verify repository received the call with the attachment
        assertEquals(1, fakeRepository.sendCalls.size)
        val lastCall = fakeRepository.sendCalls.first()
        assertEquals("Analyze this screenshot", lastCall.content)
        assertEquals(1, lastCall.attachments.size)
        assertEquals("screen.png", lastCall.attachments[0].name)
    }

    @Test
    fun `sending empty prompt with attachment succeeds and forwards attachment`() = runTest(testDispatcher) {
        advanceUntilIdle()

        val docAtt = Attachment(
            metadata = AttachmentMetadata(
                id = "doc-pdf",
                name = "whitepaper.pdf",
                mimeType = "application/pdf",
                sizeBytes = 8192,
                type = AttachmentType.PDF
            ),
            base64Data = "fakePdfBase64"
        )

        // No input text, only attachment
        viewModel.onEvent(ChatUiEvent.InputTextChanged(""))
        viewModel.onEvent(ChatUiEvent.AttachmentsSelected(listOf(docAtt)))
        advanceUntilIdle()

        viewModel.onEvent(ChatUiEvent.SendClicked)
        advanceUntilIdle()

        assertEquals(1, fakeRepository.sendCalls.size)
        assertEquals("whitepaper.pdf", fakeRepository.sendCalls[0].attachments[0].name)
        assertTrue(viewModel.uiState.value.pendingAttachments.isEmpty())
    }

    @Test
    fun `rapid duplicate send while generating is blocked`() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Simulate active generation
        fakeRepository.setGenerating(true)

        viewModel.onEvent(ChatUiEvent.InputTextChanged("Hello Mayra"))
        viewModel.onEvent(ChatUiEvent.SendClicked)
        advanceUntilIdle()

        // No call should have been dispatched because repository is generating
        assertEquals(0, fakeRepository.sendCalls.size)
        // Input text should be preserved so user does not lose their typed prompt
        assertEquals("Hello Mayra", viewModel.uiState.value.inputText)
    }

    @Test
    fun `attachment error sets banner error and closes attachment picker`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onEvent(ChatUiEvent.OpenAttachmentPicker)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isAttachmentPickerOpen)

        viewModel.onEvent(ChatUiEvent.AttachmentError("Selected file exceeds maximum limit of 20MB"))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAttachmentPickerOpen)
        assertEquals("Selected file exceeds maximum limit of 20MB", viewModel.uiState.value.bannerError)
    }

    @Test
    fun `theme toggle changes dark theme state`() = runTest(testDispatcher) {
        advanceUntilIdle()
        val initial = viewModel.uiState.value.isDarkTheme

        viewModel.onEvent(ChatUiEvent.ToggleTheme)
        advanceUntilIdle()

        assertEquals(!initial, viewModel.uiState.value.isDarkTheme)
    }

    private class FakeChatRepository : ChatRepository {
        data class SendCall(
            val content: String,
            val config: AiModelConfig,
            val attachments: List<Attachment>
        )

        val sendCalls = mutableListOf<SendCall>()

        private val initialConv = Conversation(
            id = "conv-1",
            title = "New Chat",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        private val _activeConversation = MutableStateFlow<Conversation?>(initialConv)
        override val activeConversation: StateFlow<Conversation?> = _activeConversation.asStateFlow()

        private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
        override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

        private val _conversations = MutableStateFlow<List<Conversation>>(listOf(initialConv))
        override val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

        private val _memories = MutableStateFlow<List<MemoryItem>>(emptyList())
        override val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow()

        private val _isMemoryEnabled = MutableStateFlow(true)
        override val isMemoryEnabled: StateFlow<Boolean> = _isMemoryEnabled.asStateFlow()

        private val _isGenerating = MutableStateFlow(false)
        override val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

        private val _searchPhase = MutableStateFlow(SearchPhase.IDLE)
        override val searchPhase: StateFlow<SearchPhase> = _searchPhase.asStateFlow()

        fun setGenerating(generating: Boolean) {
            _isGenerating.value = generating
        }

        override suspend fun startNewConversation(title: String): Conversation {
            val conv = Conversation(id = UUID.randomUUID().toString(), title = title)
            _activeConversation.value = conv
            _conversations.value = listOf(conv) + _conversations.value
            return conv
        }

        override suspend fun selectConversation(conversationId: String) {
            val conv = _conversations.value.find { it.id == conversationId }
            _activeConversation.value = conv
        }

        override suspend fun renameConversation(conversationId: String, newTitle: String) {}
        override suspend fun togglePinConversation(conversationId: String) {}
        override suspend fun toggleArchiveConversation(conversationId: String) {}

        override suspend fun sendMessage(
            content: String,
            config: AiModelConfig,
            attachments: List<Attachment>
        ): Result<ChatMessage> {
            sendCalls.add(SendCall(content, config, attachments))
            val msg = ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = _activeConversation.value?.id ?: "conv-1",
                role = MessageRole.USER,
                content = content,
                attachments = attachments.map { it.metadata }
            )
            _messages.value = _messages.value + msg
            return Result.success(msg)
        }

        override suspend fun retryLastFailed(config: AiModelConfig): Result<ChatMessage> {
            return Result.success(ChatMessage(conversationId = "conv-1", role = MessageRole.ASSISTANT, content = "retried"))
        }

        override suspend fun clearMessages() {
            _messages.value = emptyList()
        }

        override suspend fun deleteConversation(conversationId: String) {}
        override suspend fun cancelGeneration() {
            _isGenerating.value = false
        }

        override fun setMemoryEnabled(enabled: Boolean) {
            _isMemoryEnabled.value = enabled
        }

        override suspend fun saveMemory(content: String, category: MemoryCategory): MemoryItem {
            val mem = MemoryItem(id = UUID.randomUUID().toString(), content = content, category = category)
            _memories.value = _memories.value + mem
            return mem
        }

        override suspend fun toggleMemoryItemEnabled(memoryId: String, enabled: Boolean) {}
        override suspend fun deleteMemory(memoryId: String) {}
        override suspend fun clearAllMemories() {
            _memories.value = emptyList()
        }

        override suspend fun searchConversations(query: String): List<Conversation> = emptyList()
    }
}
