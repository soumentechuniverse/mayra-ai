package com.example.domain.model

import java.util.UUID

data class AttachmentMetadata(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val type: AttachmentType,
    val localUri: String? = null
)
