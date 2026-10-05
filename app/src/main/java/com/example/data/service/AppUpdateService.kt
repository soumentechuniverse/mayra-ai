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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        const val GITHUB_LATEST_RELEASE_API =
            "https://api.github.com/repos/soumentechuniverse/mayra-ai/releases/latest"
    }

    suspend fun checkForUpdate(
        sourceUrl: String = GITHUB_LATEST_RELEASE_API
    ): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {

        val currentVersion = BuildConfig.VERSION_NAME
            .ifBlank { "1.0.0" }
            .removePrefix("v")
            .removePrefix("V")
            .trim()

        try {
            val request = Request.Builder()
                .url(
                    sourceUrl.ifBlank {
                        GITHUB_LATEST_RELEASE_API
                    }
                )
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MayraAI-Android")
                .build()

            client.newCall(request).execute().use { response ->

                // No GitHub Release has been published yet.
                // This is not an app error.
                if (response.code == 404) {
                    return@withContext Result.success(
                        AppUpdateInfo(
                            currentVersion = currentVersion,
                            latestVersion = currentVersion,
                            isUpdateAvailable = false,
                            releaseNotes = "No public update has been published yet.",
                            downloadUrl = "",
                            releaseDate = null
                        )
                    )
                }

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Update check failed (HTTP ${response.code})."
                        )
                    )
                }

                val body = response.body?.string()

                if (body.isNullOrBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("Empty update information.")
                    )
                }

                val json = JSONObject(body)

                val remoteTag = json.optString(
                    "tag_name",
                    json.optString("version", "")
                )

                val latestVersion = cleanVersion(remoteTag)

                if (latestVersion.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("Invalid release version.")
                    )
                }

                val releaseNotes = json.optString(
                    "body",
                    "New features, performance improvements, and bug fixes."
                ).ifBlank {
                    "New features, performance improvements, and bug fixes."
                }

                // Find the APK from the GitHub Release assets.
                var apkUrl = ""

                val assets = json.optJSONArray("assets")

                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue

                        val fileName = asset.optString("name", "")

                        if (fileName.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString(
                                "browser_download_url",
                                ""
                            )

                            if (apkUrl.isNotBlank()) {
                                break
                            }
                        }
                    }
                }

                // If no APK asset exists, use the release page.
                if (apkUrl.isBlank()) {
                    apkUrl = json.optString("html_url", "")
                }

                val publishedAt = json.optString(
                    "published_at",
                    ""
                ).ifBlank {
                    null
                }

                val isNewer = isVersionNewer(
                    latestVersion,
                    currentVersion
                )

                Result.success(
                    AppUpdateInfo(
                        currentVersion = currentVersion,
                        latestVersion = latestVersion,
                        isUpdateAvailable = isNewer,
                        releaseNotes = releaseNotes,
                        downloadUrl = apkUrl,
                        releaseDate = publishedAt
                    )
                )
            }

        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    "Unable to check for updates.",
                    e
                )
            )
        }
    }

    /**
     * Opens the official GitHub APK/release page.
     * Installation is always user-controlled by Android.
     */
    fun openDownloadUrl(
        context: Context,
        downloadUrl: String
    ) {
        if (downloadUrl.isBlank()) return

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(downloadUrl)
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        context.startActivity(intent)
    }

    private fun cleanVersion(version: String): String {
        return version
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .substringBefore("+")
            .trim()
    }

    private fun isVersionNewer(
        remote: String,
        current: String
    ): Boolean {

        val remoteParts = versionParts(remote)
        val currentParts = versionParts(current)

        val maxSize = maxOf(
            remoteParts.size,
            currentParts.size
        )

        for (i in 0 until maxSize) {
            val remotePart = remoteParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }

            when {
                remotePart > currentPart -> return true
                remotePart < currentPart -> return false
            }
        }

        return false
    }

    private fun versionParts(version: String): List<Int> {
        return Regex("""\d+""")
            .findAll(version)
            .mapNotNull {
                it.value.toIntOrNull()
            }
            .toList()
            .ifEmpty {
                listOf(0, 0, 0)
            }
    }
}
