package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import com.example.domain.model.Attachment
import com.example.domain.model.AttachmentMetadata
import com.example.domain.model.AttachmentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID

object AttachmentHelper {

    const val MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024 // 10 MB limit
    private const val MAX_TEXT_CHARS = 250_000 // ~250k chars for text files

    private val SUPPORTED_IMAGE_MIMES = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp"
    )

    private val SUPPORTED_IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp"
    )

    private val SUPPORTED_DOC_EXTENSIONS = setOf(
        "txt", "md", "csv", "json", "xml", "html", "htm", "log",
        "kt", "java", "py", "js", "ts", "cpp", "c", "h", "css", "yaml", "yml",
        "sql", "sh", "properties", "gradle", "kts", "env"
    )

    /**
     * Resolves a Uri into an in-memory [Attachment] with validated Base64 or text content.
     */
    suspend fun processUri(context: Context, uri: Uri): Result<Attachment> = withContext(Dispatchers.IO) {
        try {
            // Attempt to retain read permission if persistable
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Not all providers support persistable permissions (e.g. Photo Picker). Safe to ignore.
            }

            val fileName = queryFileName(context, uri) ?: "attachment_${System.currentTimeMillis()}"
            val extension = getExtension(fileName).lowercase(Locale.ROOT)
            val detectedMime = detectMimeType(context, uri, extension)

            val typeResolution = resolveAttachmentType(detectedMime, extension)
                ?: return@withContext Result.failure(
                    IllegalArgumentException(
                        "Unsupported file format for '$fileName'. Supported types: JPG, PNG, WEBP, PDF, TXT, MD, CSV, JSON, and code/text files."
                    )
                )
            val (attachmentType, normalizedMime) = typeResolution

            val fileSize = queryFileSize(context, uri)
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                return@withContext Result.failure(
                    IllegalArgumentException("File '$fileName' exceeds the 10MB limit.")
                )
            }

            val inputStream = try {
                context.contentResolver.openInputStream(uri)
            } catch (e: SecurityException) {
                return@withContext Result.failure(
                    SecurityException("Permission denied reading '$fileName'. Please grant file access.")
                )
            } ?: return@withContext Result.failure(
                IllegalStateException("Could not open file '$fileName'.")
            )

            val bytes = inputStream.use { readBytesWithLimit(it, MAX_FILE_SIZE_BYTES, fileName) }
            if (bytes.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("The file '$fileName' is empty.")
                )
            }

            val base64Data = if (attachmentType == AttachmentType.IMAGE || attachmentType == AttachmentType.PDF) {
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else null

            val textContent = if (attachmentType == AttachmentType.DOCUMENT) {
                try {
                    val rawText = String(bytes, Charsets.UTF_8).take(MAX_TEXT_CHARS)
                    if (rawText.isBlank()) {
                        return@withContext Result.failure(
                            IllegalArgumentException("Document '$fileName' contains no readable text.")
                        )
                    }
                    rawText
                } catch (e: Exception) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Unable to decode text content from '$fileName'.")
                    )
                }
            } else null

            val metadata = AttachmentMetadata(
                id = UUID.randomUUID().toString(),
                name = fileName,
                mimeType = normalizedMime,
                sizeBytes = bytes.size.toLong(),
                type = attachmentType,
                localUri = uri.toString()
            )

            Result.success(
                Attachment(
                    id = metadata.id,
                    name = metadata.name,
                    mimeType = metadata.mimeType,
                    sizeBytes = metadata.sizeBytes,
                    type = metadata.type,
                    localUri = metadata.localUri,
                    base64Data = base64Data,
                    textContent = textContent
                )
            )
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException("Failed to process file: ${e.localizedMessage ?: "Unknown error"}")
            )
        }
    }

    fun resolveAttachmentType(mime: String, extension: String): Pair<AttachmentType, String>? {
        val lowerMime = mime.lowercase(Locale.ROOT)
        val lowerExt = extension.lowercase(Locale.ROOT)
        return when {
            SUPPORTED_IMAGE_EXTENSIONS.contains(lowerExt) || SUPPORTED_IMAGE_MIMES.contains(lowerMime) -> {
                val normalized = when {
                    lowerExt == "png" || lowerMime == "image/png" -> "image/png"
                    lowerExt == "webp" || lowerMime == "image/webp" -> "image/webp"
                    else -> "image/jpeg"
                }
                Pair(AttachmentType.IMAGE, normalized)
            }
            lowerExt == "pdf" || lowerMime == "application/pdf" -> {
                Pair(AttachmentType.PDF, "application/pdf")
            }
            SUPPORTED_DOC_EXTENSIONS.contains(lowerExt) ||
            lowerMime.startsWith("text/") ||
            lowerMime == "application/json" ||
            lowerMime == "application/csv" ||
            lowerMime == "application/x-yaml" ||
            lowerMime == "application/xml" -> {
                val normalized = when {
                    lowerExt == "json" || lowerMime == "application/json" -> "application/json"
                    lowerExt == "csv" || lowerMime == "text/csv" -> "text/csv"
                    lowerExt == "md" || lowerMime == "text/markdown" -> "text/markdown"
                    else -> "text/plain"
                }
                Pair(AttachmentType.DOCUMENT, normalized)
            }
            else -> null
        }
    }

    private fun detectMimeType(context: Context, uri: Uri, extension: String): String {
        val fromResolver = context.contentResolver.getType(uri)
        if (!fromResolver.isNullOrBlank()) {
            return fromResolver
        }
        if (extension.isNotEmpty()) {
            val fromExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (!fromExtension.isNullOrBlank()) {
                return fromExtension
            }
        }
        return "application/octet-stream"
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        return cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                // Fall through to lastPathSegment
            }
        }
        return uri.lastPathSegment
    }

    private fun queryFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && cursor.moveToFirst()) {
                        return cursor.getLong(sizeIndex)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return 0L
    }

    private fun readBytesWithLimit(stream: InputStream, limit: Long, fileName: String = ""): ByteArray {
        val buffer = ByteArray(8192)
        val baos = ByteArrayOutputStream()
        var totalRead = 0L
        var read: Int
        while (stream.read(buffer).also { read = it } != -1) {
            totalRead += read
            if (totalRead > limit) {
                val label = if (fileName.isNotBlank()) " '$fileName'" else ""
                throw IllegalArgumentException("File$label exceeds the 10MB limit.")
            }
            baos.write(buffer, 0, read)
        }
        return baos.toByteArray()
    }

    private fun getExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot != -1 && lastDot < fileName.length - 1) {
            fileName.substring(lastDot + 1)
        } else {
            ""
        }
    }
}
