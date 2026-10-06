package com.example.domain.model

import java.util.Locale
import java.util.UUID

data class AttachmentMetadata(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val type: AttachmentType,
    val localUri: String? = null
) {
    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return ""
            if (sizeBytes < 1024) return "$sizeBytes B"
            val kb = sizeBytes / 1024.0
            if (kb < 1024) {
                return if (sizeBytes % 1024L == 0L) "${sizeBytes / 1024} KB"
                else String.format(Locale.ROOT, "%.1f KB", kb)
            }
            val mb = kb / 1024.0
            return String.format(Locale.ROOT, "%.1f MB", mb)
        }
}
