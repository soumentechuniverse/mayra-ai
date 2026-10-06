package com.example.domain.model

import java.util.Locale
import java.util.UUID

data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val type: AttachmentType = AttachmentType.DOCUMENT,
    val localUri: String? = null,
    val base64Data: String? = null,
    val textContent: String? = null
) {
    constructor(
        metadata: AttachmentMetadata,
        base64Data: String? = null,
        textContent: String? = null
    ) : this(
        id = metadata.id,
        name = metadata.name,
        mimeType = metadata.mimeType,
        sizeBytes = metadata.sizeBytes,
        type = metadata.type,
        localUri = metadata.localUri,
        base64Data = base64Data,
        textContent = textContent
    )

    val metadata: AttachmentMetadata
        get() = AttachmentMetadata(
            id = id,
            name = name,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            type = type,
            localUri = localUri
        )

    val formattedSize: String
        get() = metadata.formattedSize
}
