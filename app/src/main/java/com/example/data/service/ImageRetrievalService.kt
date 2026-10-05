package com.example.data.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class RetrievedImageResult(
    val title: String,
    val imageUrl: String,
    val sourceUrl: String,
    val attribution: String = "Wikimedia Commons (CC BY-SA / Public Domain)",
    val description: String? = null
)

interface ImageRetrievalService {
    suspend fun searchImages(query: String, maxResults: Int = 3): List<RetrievedImageResult>
}

class WikimediaImageRetrievalService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) : ImageRetrievalService {

    override suspend fun searchImages(query: String, maxResults: Int): List<RetrievedImageResult> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext emptyList()

            kotlinx.coroutines.withTimeoutOrNull(4000L) {
                // Try English / multilingual Wikipedia search first, fallback to Wikimedia Commons
                val results = searchWikiSite("en.wikipedia.org", trimmed, maxResults)
                if (results.isNotEmpty()) {
                    return@withTimeoutOrNull results
                }

                // Try Commons
                searchWikiSite("commons.wikimedia.org", trimmed, maxResults)
            } ?: emptyList()
        }

    private fun searchWikiSite(host: String, query: String, maxResults: Int): List<RetrievedImageResult> {
        return try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val url = "https://$host/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrlimit=$maxResults&prop=pageimages|info&piprop=original|thumbnail&pithumbsize=800&inprop=url&format=json"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MayraAI-Android/1.0 (PublicImageRetrieval; soumentechuniverse@gmail.com)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val root = JSONObject(body)

                if (!root.has("query")) return emptyList()
                val queryObj = root.getJSONObject("query")
                if (!queryObj.has("pages")) return emptyList()

                val pages = queryObj.getJSONObject("pages")
                val results = mutableListOf<RetrievedImageResult>()

                val keys = pages.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val page = pages.getJSONObject(key)

                    val title = page.optString("title", "Image")
                    val sourceUrl = page.optString("canonicalurl", page.optString("fullurl", "https://$host"))

                    var imgUrl: String? = null
                    if (page.has("thumbnail")) {
                        imgUrl = page.getJSONObject("thumbnail").optString("source")
                    } else if (page.has("original")) {
                        imgUrl = page.getJSONObject("original").optString("source")
                    }

                    if (!imgUrl.isNullOrBlank() && (imgUrl.endsWith(".jpg", ignoreCase = true) ||
                                imgUrl.endsWith(".jpeg", ignoreCase = true) ||
                                imgUrl.endsWith(".png", ignoreCase = true) ||
                                imgUrl.endsWith(".webp", ignoreCase = true) ||
                                imgUrl.contains("/thumb/"))) {
                        results.add(
                            RetrievedImageResult(
                                title = title,
                                imageUrl = imgUrl,
                                sourceUrl = sourceUrl,
                                attribution = "Wikimedia Commons / Wikipedia (CC BY-SA / Public Domain)",
                                description = title
                            )
                        )
                    }
                }
                results
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
