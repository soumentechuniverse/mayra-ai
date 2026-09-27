package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ChatMessage
import com.example.domain.model.MessageRole
import com.example.domain.model.MessageStatus

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
    val errorMessage: String? = null
) {
    fun toDomain(): ChatMessage = ChatMessage(
        id = id,
        conversationId = conversationId,
        role = try { MessageRole.valueOf(role) } catch (e: Exception) { MessageRole.ASSISTANT },
        content = content,
        timestamp = timestamp,
        status = try { MessageStatus.valueOf(status) } catch (e: Exception) { MessageStatus.SENT },
        errorMessage = errorMessage
    )

    companion object {
        fun fromDomain(message: ChatMessage): ChatMessageEntity = ChatMessageEntity(
            id = message.id,
            conversationId = message.conversationId,
            role = message.role.name,
            content = message.content,
            timestamp = message.timestamp,
            status = message.status.name,
            errorMessage = message.errorMessage
        )
    }
}
