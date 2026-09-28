package com.example.data.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val isUpdateAvailable: Boolean,
    val releaseNotes: String,
    val downloadUrl: String,
    val releaseDate: String? = null
)

class AppUpdateService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    suspend fun checkForUpdate(sourceUrl: String): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val currentVersion = BuildConfig.VERSION_NAME.ifBlank { "1.0" }
            val request = Request.Builder()
                .url(sourceUrl)
                .header("Accept", "application/vnd.github.v3+json, application/json")
                .header("User-Agent", "MayraAI-Android-UpdateChecker")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Unable to fetch update details (HTTP ${response.code}).")
                    )
                }

                val bodyString = response.body?.string()
                    ?: return@withContext Result.failure(IllegalStateException("Empty update response from server."))

                val json = JSONObject(bodyString)

                // Supports both standard GitHub Release API schema and simple JSON release schema
                val tagName = when {
                    json.has("tag_name") -> json.getString("tag_name")
                    json.has("version") -> json.getString("version")
                    json.has("name") -> json.getString("name")
                    else -> "1.0"
                }

                val cleanRemoteVersion = tagName.trim().removePrefix("v").removePrefix("V")
                val cleanCurrentVersion = currentVersion.trim().removePrefix("v").removePrefix("V")

                val releaseNotes = when {
                    json.has("body") && json.getString("body").isNotBlank() -> json.getString("body")
                    json.has("notes") && json.getString("notes").isNotBlank() -> json.getString("notes")
                    else -> "New features, performance enhancements, and bug fixes."
                }

                // Locate APK asset or fallback to html release URL
                var downloadUrl = ""
                if (json.has("assets")) {
                    val assets = json.getJSONArray("assets")
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                if (downloadUrl.isBlank()) {
                    downloadUrl = when {
                        json.has("download_url") -> json.getString("download_url")
                        json.has("downloadUrl") -> json.getString("downloadUrl")
                        json.has("html_url") -> json.getString("html_url")
                        else -> sourceUrl
                    }
                }

                val publishedAt = json.optString("published_at", json.optString("date", null))

                val isNewer = isVersionNewer(cleanRemoteVersion, cleanCurrentVersion)

                Result.success(
                    AppUpdateInfo(
                        currentVersion = currentVersion,
                        latestVersion = cleanRemoteVersion,
                        isUpdateAvailable = isNewer,
                        releaseNotes = releaseNotes,
                        downloadUrl = downloadUrl,
                        releaseDate = publishedAt
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Safely initiates user-directed download/installation via standard system Intent.
     * Never performs silent installation.
     */
    fun openDownloadUrl(context: Context, downloadUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun isVersionNewer(remote: String, current: String): Boolean {
        try {
            val remoteParts = remote.split(".").mapNotNull { it.filter { char -> char.isDigit() }.toIntOrNull() }
            val currentParts = current.split(".").mapNotNull { it.filter { char -> char.isDigit() }.toIntOrNull() }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        } catch (e: Exception) {
            return remote != current && remote > current
        }
    }
}
