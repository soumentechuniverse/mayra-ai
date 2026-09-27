package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.MemoryCategory
import com.example.domain.model.MemoryItem

@Entity(tableName = "memories")
data class MemoryItemEntity(
    @PrimaryKey val id: String,
    val content: String,
    val category: String,
    val createdAt: Long,
    val updatedAt: Long,
    val enabled: Boolean
) {
    fun toDomain(): MemoryItem = MemoryItem(
        id = id,
        content = content,
        category = try {
            MemoryCategory.valueOf(category)
        } catch (e: Exception) {
            MemoryCategory.OTHER
        },
        createdAt = createdAt,
        updatedAt = updatedAt,
        enabled = enabled
    )

    companion object {
        fun fromDomain(item: MemoryItem): MemoryItemEntity = MemoryItemEntity(
            id = item.id,
            content = item.content,
            category = item.category.name,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt,
            enabled = item.enabled
        )
    }
}
