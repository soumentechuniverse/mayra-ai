package com.example.domain.model

/**
 * Represents a verifiable web source / citation retrieved from Grounding with Google Search.
 */
data class SearchSource(
    val title: String,
    val url: String,
    val domain: String = extractDomain(url),
    val snippet: String? = null,
    val publicationDate: String? = null,
    val relevanceScore: Float? = null
) {
    companion object {
        fun extractDomain(url: String): String {
            return try {
                val clean = url.trim()
                    .removePrefix("http://")
                    .removePrefix("https://")
                val endSlash = clean.indexOf('/')
                val host = if (endSlash != -1) clean.substring(0, endSlash) else clean
                val portIdx = host.indexOf(':')
                val hostWithoutPort = if (portIdx != -1) host.substring(0, portIdx) else host
                hostWithoutPort.removePrefix("www.")
            } catch (e: Exception) {
                ""
            }
        }
    }
}
