package com.example

import com.example.data.local.entity.ChatMessageEntity
import com.example.domain.model.AttachmentMetadata
import com.example.domain.model.AttachmentType
import com.example.domain.model.ChatMessage
import com.example.domain.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultimodalAttachmentTest {

    @Test
    fun `attachment metadata formats file size correctly`() {
        val small = AttachmentMetadata(
            name = "file.txt",
            mimeType = "text/plain",
            sizeBytes = 512,
            type = AttachmentType.DOCUMENT
        )
        assertEquals("512 B", small.formattedSize)

        val kb = AttachmentMetadata(
            name = "pic.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 2048,
            type = AttachmentType.IMAGE
        )
        assertEquals("2 KB", kb.formattedSize)

        val mb = AttachmentMetadata(
            name = "doc.pdf",
            mimeType = "application/pdf",
            sizeBytes = 2 * 1024 * 1024,
            type = AttachmentType.PDF
        )
        assertEquals("2.0 MB", mb.formattedSize)
    }

    @Test
    fun `chat message entity serializes and deserializes attachment metadata`() {
        val attachment = AttachmentMetadata(
            id = "att-123",
            name = "diagram.png",
            mimeType = "image/png",
            sizeBytes = 1048576,
            type = AttachmentType.IMAGE,
            localUri = "content://media/external/images/media/1"
        )
        val message = ChatMessage(
            id = "msg-1",
            conversationId = "conv-1",
            role = MessageRole.USER,
            content = "Look at this chart",
            attachments = listOf(attachment)
        )

        val entity = ChatMessageEntity.fromDomain(message)
        assertNotNull(entity.attachmentsJson)

        val restored = entity.toDomain()
        assertEquals(1, restored.attachments.size)
        assertEquals("diagram.png", restored.attachments.first().name)
        assertEquals("image/png", restored.attachments.first().mimeType)
        assertEquals(AttachmentType.IMAGE, restored.attachments.first().type)
        assertEquals(
            "content://media/external/images/media/1",
            restored.attachments.first().localUri
        )
    }

    @Test
    fun `attachment helper recognizes supported formats and rejects executables`() {
        val jpg = com.example.util.AttachmentHelper.resolveAttachmentType("image/jpeg", "jpg")
        assertNotNull(jpg)
        assertEquals(AttachmentType.IMAGE, jpg!!.first)
        assertEquals("image/jpeg", jpg.second)

        val png = com.example.util.AttachmentHelper.resolveAttachmentType("image/png", "png")
        assertNotNull(png)
        assertEquals(AttachmentType.IMAGE, png!!.first)

        val pdf = com.example.util.AttachmentHelper.resolveAttachmentType("application/pdf", "pdf")
        assertNotNull(pdf)
        assertEquals(AttachmentType.PDF, pdf!!.first)

        val csv = com.example.util.AttachmentHelper.resolveAttachmentType("text/csv", "csv")
        assertNotNull(csv)
        assertEquals(AttachmentType.DOCUMENT, csv!!.first)

        val unsupported = com.example.util.AttachmentHelper.resolveAttachmentType(
            "application/x-msdownload", "exe"
        )
        assertNull(unsupported)
    }
}
