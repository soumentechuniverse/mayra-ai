package com.example.domain.model

import java.util.UUID

enum class MemoryCategory(val displayName: String) {
    PREFERENCE("Preference"),
    PROFILE("Profile"),
    INSTRUCTION("Instruction"),
    PROJECT("Project"),
    OTHER("Other")
}

data class MemoryItem(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val category: MemoryCategory = MemoryCategory.OTHER,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val enabled: Boolean = true
)
