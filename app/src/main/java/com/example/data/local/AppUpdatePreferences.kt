package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages configurable update settings for Mayra AI.
 * The default source must match the public repository that publishes releases.
 */
class AppUpdatePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _updateSourceUrl = MutableStateFlow(
        prefs.getString(KEY_UPDATE_URL, DEFAULT_UPDATE_URL) ?: DEFAULT_UPDATE_URL
    )
    val updateSourceUrl: StateFlow<String> = _updateSourceUrl.asStateFlow()

    fun getUpdateUrl(): String = _updateSourceUrl.value

    fun setUpdateUrl(url: String) {
        val trimmed = url.trim()
        val effective = if (trimmed.isNotBlank()) trimmed else DEFAULT_UPDATE_URL
        prefs.edit().putString(KEY_UPDATE_URL, effective).apply()
        _updateSourceUrl.value = effective
    }

    fun resetToDefault() {
        setUpdateUrl(DEFAULT_UPDATE_URL)
    }

    companion object {
        private const val PREFS_NAME = "mayra_update_preferences"
        private const val KEY_UPDATE_URL = "update_source_url"
        const val DEFAULT_UPDATE_URL =
            "https://api.github.com/repos/soumentechuniverse/mayra-ai/releases/latest"
    }
}
