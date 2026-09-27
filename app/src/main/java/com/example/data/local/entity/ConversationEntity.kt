package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.Conversation

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val preview: String,
    val messageCount: Int,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false
) {
    fun toDomain(): Conversation = Conversation(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        preview = preview,
        messageCount = messageCount,
        isPinned = isPinned,
        isArchived = isArchived
    )

    companion object {
        fun fromDomain(conversation: Conversation): ConversationEntity = ConversationEntity(
            id = conversation.id,
            title = conversation.title,
            createdAt = conversation.createdAt,
            updatedAt = conversation.updatedAt,
            preview = conversation.preview,
            messageCount = conversation.messageCount,
            isPinned = conversation.isPinned,
            isArchived = conversation.isArchived
        )
    }
}
