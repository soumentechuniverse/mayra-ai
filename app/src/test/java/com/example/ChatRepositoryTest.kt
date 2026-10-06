package com.example

import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.repository.ChatRepository
import com.example.data.repository.StreamEvent
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryTest {

    private val fakeRepo = object : ChatRepository {
        private val conversations = mutableListOf<ConversationEntity>()
        private val messages = mutableListOf<MessageEntity>()

        override fun getConversations() = flowOf(conversations)
        override fun getArchivedConversations() = flowOf(conversations.filter { it.isArchived })
        override fun searchConversations(query: String) = flowOf(conversations.filter { it.title.contains(query, true) })
        override suspend fun getConversationById(id: String) = conversations.find { it.id == id }

        override suspend fun createConversation(title: String): ConversationEntity {
            val c = ConversationEntity(id = "conv_1", title = title)
            conversations.add(c)
            return c
        }

        override suspend fun updateConversationTitle(id: String, newTitle: String) {
            val idx = conversations.indexOfFirst { it.id == id }
            if (idx >= 0) conversations[idx] = conversations[idx].copy(title = newTitle)
        }

        override suspend fun togglePinConversation(id: String, isPinned: Boolean) {
            val idx = conversations.indexOfFirst { it.id == id }
            if (idx >= 0) conversations[idx] = conversations[idx].copy(isPinned = isPinned)
        }

        override suspend fun toggleArchiveConversation(id: String, isArchived: Boolean) {
            val idx = conversations.indexOfFirst { it.id == id }
            if (idx >= 0) conversations[idx] = conversations[idx].copy(isArchived = isArchived)
        }

        override suspend fun deleteConversation(id: String) {
            conversations.removeAll { it.id == id }
        }

        override fun getMessagesForConversation(conversationId: String) =
            flowOf(messages.filter { it.conversationId == conversationId })

        override suspend fun deleteMessage(messageId: String) {
            messages.removeAll { it.id == messageId }
        }

        override suspend fun sendMessage(
            conversationId: String,
            userPrompt: String,
            attachmentBytes: ByteArray?,
            attachmentMimeType: String?,
            attachmentName: String?,
            attachmentUriString: String?
        ) = flowOf(
            StreamEvent.TextChunk("Hello "),
            StreamEvent.TextChunk("World"),
            StreamEvent.Completed("Hello World", emptyList())
        )

        override suspend fun retryMessage(conversationId: String, failedMessageId: String) =
            flowOf(StreamEvent.Completed("Retried response", emptyList()))
    }

    @Test
    fun testConversationCreationAndPin() = runBlocking {
        val conv = fakeRepo.createConversation("Test Chat")
        assertEquals("Test Chat", conv.title)

        fakeRepo.togglePinConversation(conv.id, true)
        val updated = fakeRepo.getConversationById(conv.id)
        assertNotNull(updated)
        assertTrue(updated?.isPinned == true)
    }

    @Test
    fun testSendMessageStream() = runBlocking {
        val events = mutableListOf<StreamEvent>()
        fakeRepo.sendMessage("conv_1", "Hello").collect {
            events.add(it)
        }
        assertEquals(3, events.size)
        assertTrue(events[0] is StreamEvent.TextChunk)
        assertTrue(events[2] is StreamEvent.Completed)
    }
}
