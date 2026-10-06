package com.example.domain.model

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
                java.net.URI(url).host?.removePrefix("www.") ?: url
            } catch (_: Exception) {
                url
            }
        }
    }
}
