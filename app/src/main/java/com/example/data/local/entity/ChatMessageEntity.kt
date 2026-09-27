package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.AttachmentMetadata
import com.example.domain.model.AttachmentType
import com.example.domain.model.ChatMessage
import com.example.domain.model.MessageRole
import com.example.domain.model.MessageStatus
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val status: String,
    val errorMessage: String? = null,
    val attachmentsJson: String? = null
) {
    fun toDomain(): ChatMessage = ChatMessage(
        id = id,
        conversationId = conversationId,
        role = try { MessageRole.valueOf(role) } catch (e: Exception) { MessageRole.ASSISTANT },
        content = content,
        timestamp = timestamp,
        status = try { MessageStatus.valueOf(status) } catch (e: Exception) { MessageStatus.SENT },
        errorMessage = errorMessage,
        attachments = deserializeAttachments(attachmentsJson)
    )

    companion object {
        fun fromDomain(message: ChatMessage): ChatMessageEntity = ChatMessageEntity(
            id = message.id,
            conversationId = message.conversationId,
            role = message.role.name,
            content = message.content,
            timestamp = message.timestamp,
            status = message.status.name,
            errorMessage = message.errorMessage,
            attachmentsJson = serializeAttachments(message.attachments)
        )

        private fun serializeAttachments(list: List<AttachmentMetadata>): String? {
            if (list.isEmpty()) return null
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("name", item.name)
                obj.put("mimeType", item.mimeType)
                obj.put("sizeBytes", item.sizeBytes)
                obj.put("type", item.type.name)
                item.localUri?.let { obj.put("localUri", it) }
                array.put(obj)
            }
            return array.toString()
        }

        private fun deserializeAttachments(json: String?): List<AttachmentMetadata> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                val list = mutableListOf<AttachmentMetadata>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        AttachmentMetadata(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.optString("name", "File"),
                            mimeType = obj.optString("mimeType", "application/octet-stream"),
                            sizeBytes = obj.optLong("sizeBytes", 0L),
                            type = try {
                                AttachmentType.valueOf(obj.optString("type"))
                            } catch (e: Exception) {
                                AttachmentType.DOCUMENT
                            },
                            localUri = obj.optString("localUri").takeIf { it.isNotBlank() }
                        )
                    )
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
