package com.example.domain.model

import java.util.UUID

enum class AttachmentType {
    IMAGE,
    PDF,
    DOCUMENT
}

/**
 * Lightweight metadata for attachments stored in message history and Room database.
 * Does not contain raw binary or Base64 data.
 */
data class AttachmentMetadata(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val type: AttachmentType,
    val localUri: String? = null
) {
    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return ""
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
                kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB", kb)
                else -> "$sizeBytes B"
            }
        }
}

/**
 * In-memory active attachment holding base64 payload or extracted text
 * for sending to Gemini API.
 */
data class Attachment(
    val metadata: AttachmentMetadata,
    val base64Data: String? = null,
    val textContent: String? = null
) {
    val id: String get() = metadata.id
    val name: String get() = metadata.name
    val mimeType: String get() = metadata.mimeType
    val sizeBytes: Long get() = metadata.sizeBytes
    val type: AttachmentType get() = metadata.type
    val localUri: String? get() = metadata.localUri
    val formattedSize: String get() = metadata.formattedSize
}
