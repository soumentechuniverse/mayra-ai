package com.example

import com.example.data.local.entity.ChatMessageEntity
import com.example.data.service.GeminiService
import com.example.domain.model.AiModelConfig
import com.example.domain.model.Attachment
import com.example.domain.model.AttachmentMetadata
import com.example.domain.model.AttachmentType
import com.example.domain.model.ChatMessage
import com.example.domain.model.MessageRole
import com.example.domain.model.MessageStatus
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    fun `chat message entity serializes and deserializes attachment metadata correctly`() {
        val att = AttachmentMetadata(
            id = "att-123",
            name = "diagram.png",
            mimeType = "image/png",
            sizeBytes = 1048576,
            type = AttachmentType.IMAGE,
            localUri = "content://media/external/images/media/1"
        )
        val msg = ChatMessage(
            id = "msg-1",
            conversationId = "conv-1",
            role = MessageRole.USER,
            content = "Look at this chart",
            attachments = listOf(att)
        )

        val entity = ChatMessageEntity.fromDomain(msg)
        assertNotNull(entity.attachmentsJson)

        val restored = entity.toDomain()
        assertEquals(1, restored.attachments.size)
        assertEquals("diagram.png", restored.attachments.first().name)
        assertEquals("image/png", restored.attachments.first().mimeType)
        assertEquals(AttachmentType.IMAGE, restored.attachments.first().type)
        assertEquals("content://media/external/images/media/1", restored.attachments.first().localUri)
    }

    @Test
    fun `geminiService sends multimodal request with image inlineData and user prompt`() = runBlocking {
        var capturedRequestBody: String? = null

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val buffer = okio.Buffer()
                request.body?.writeTo(buffer)
                capturedRequestBody = buffer.readUtf8()

                val sseData = """
                    data: {"candidates": [{"content": {"parts": [{"text": "I see a futuristic city skyline."}], "role": "model"}}]}

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

        val imageAttachment = Attachment(
            metadata = AttachmentMetadata(
                name = "city.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 1024,
                type = AttachmentType.IMAGE
            ),
            base64Data = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        )

        val chunks = service.generateStream(
            conversationId = "conv-1",
            prompt = "What is in this picture?",
            history = emptyList(),
            config = AiModelConfig(),
            attachments = listOf(imageAttachment)
        ).toList()

        assertEquals(2, chunks.size)
        assertEquals("I see a futuristic city skyline.", chunks[0].textDelta)

        assertNotNull(capturedRequestBody)
        val json = JSONObject(capturedRequestBody!!)
        val contents = json.getJSONArray("contents")
        assertEquals(1, contents.length())

        val parts = contents.getJSONObject(0).getJSONArray("parts")
        assertEquals(2, parts.length())

        val inlinePart = parts.getJSONObject(0).getJSONObject("inlineData")
        assertEquals("image/jpeg", inlinePart.getString("mimeType"))
        assertEquals(imageAttachment.base64Data, inlinePart.getString("data"))

        val textPart = parts.getJSONObject(1)
        assertEquals("What is in this picture?", textPart.getString("text"))
    }

    @Test
    fun `geminiService sends PDF attachment with sensible default instruction when text is empty`() = runBlocking {
        var capturedRequestBody: String? = null

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val buffer = okio.Buffer()
                request.body?.writeTo(buffer)
                capturedRequestBody = buffer.readUtf8()

                val sseData = """
                    data: {"candidates": [{"content": {"parts": [{"text": "Summary of report."}], "role": "model"}}]}

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

        val pdfAttachment = Attachment(
            metadata = AttachmentMetadata(
                name = "document.pdf",
                mimeType = "application/pdf",
                sizeBytes = 4096,
                type = AttachmentType.PDF
            ),
            base64Data = "JVBERi0xLjQKJcTl8uXrp/Og0MTGCjQgMCBvYmoKPDw..."
        )

        val chunks = service.generateStream(
            conversationId = "conv-1",
            prompt = "",
            history = emptyList(),
            config = AiModelConfig(),
            attachments = listOf(pdfAttachment)
        ).toList()

        assertEquals(2, chunks.size)
        assertNotNull(capturedRequestBody)

        val json = JSONObject(capturedRequestBody!!)
        val contents = json.getJSONArray("contents")
        val parts = contents.getJSONObject(0).getJSONArray("parts")

        val inlinePart = parts.getJSONObject(0).getJSONObject("inlineData")
        assertEquals("application/pdf", inlinePart.getString("mimeType"))

        val textPart = parts.getJSONObject(1).getString("text")
        assertTrue(textPart.contains("Analyze this document"))
    }

    @Test
    fun `geminiService sends text document attachment safely formatted`() = runBlocking {
        var capturedRequestBody: String? = null

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val buffer = okio.Buffer()
                request.body?.writeTo(buffer)
                capturedRequestBody = buffer.readUtf8()

                val sseData = """
                    data: {"candidates": [{"content": {"parts": [{"text": "Found a syntax bug."}], "role": "model"}}]}

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

        val docAttachment = Attachment(
            metadata = AttachmentMetadata(
                name = "main.py",
                mimeType = "text/plain",
                sizeBytes = 120,
                type = AttachmentType.DOCUMENT
            ),
            textContent = "def hello():\n    print('Hello World')\n"
        )

        val chunks = service.generateStream(
            conversationId = "conv-1",
            prompt = "Review this code",
            history = emptyList(),
            config = AiModelConfig(),
            attachments = listOf(docAttachment)
        ).toList()

        assertEquals(2, chunks.size)
        assertNotNull(capturedRequestBody)

        val json = JSONObject(capturedRequestBody!!)
        val contents = json.getJSONArray("contents")
        val parts = contents.getJSONObject(0).getJSONArray("parts")
        assertEquals(2, parts.length())

        val docTextPart = parts.getJSONObject(0).getString("text")
        assertTrue(docTextPart.contains("main.py"))
        assertTrue(docTextPart.contains("def hello():"))

        val promptPart = parts.getJSONObject(1).getString("text")
        assertEquals("Review this code", promptPart)
    }

    @Test
    fun `attachmentHelper resolves types correctly and rejects unsupported`() {
        val jpgType = com.example.util.AttachmentHelper.resolveAttachmentType("image/jpeg", "jpg")
        assertNotNull(jpgType)
        assertEquals(AttachmentType.IMAGE, jpgType!!.first)
        assertEquals("image/jpeg", jpgType.second)

        val pngType = com.example.util.AttachmentHelper.resolveAttachmentType("image/png", "png")
        assertNotNull(pngType)
        assertEquals(AttachmentType.IMAGE, pngType!!.first)
        assertEquals("image/png", pngType.second)

        val webpType = com.example.util.AttachmentHelper.resolveAttachmentType("image/webp", "webp")
        assertNotNull(webpType)
        assertEquals(AttachmentType.IMAGE, webpType!!.first)
        assertEquals("image/webp", webpType.second)

        val pdfType = com.example.util.AttachmentHelper.resolveAttachmentType("application/pdf", "pdf")
        assertNotNull(pdfType)
        assertEquals(AttachmentType.PDF, pdfType!!.first)

        val csvType = com.example.util.AttachmentHelper.resolveAttachmentType("text/csv", "csv")
        assertNotNull(csvType)
        assertEquals(AttachmentType.DOCUMENT, csvType!!.first)

        val jsonType = com.example.util.AttachmentHelper.resolveAttachmentType("application/json", "json")
        assertNotNull(jsonType)
        assertEquals(AttachmentType.DOCUMENT, jsonType!!.first)

        val pyType = com.example.util.AttachmentHelper.resolveAttachmentType("text/x-python", "py")
        assertNotNull(pyType)
        assertEquals(AttachmentType.DOCUMENT, pyType!!.first)

        val unsupported = com.example.util.AttachmentHelper.resolveAttachmentType("application/x-msdownload", "exe")
        org.junit.Assert.assertNull(unsupported)
    }
}
