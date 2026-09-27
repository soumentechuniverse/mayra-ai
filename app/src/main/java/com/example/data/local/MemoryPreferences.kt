package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MemoryPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mayra_memory_prefs", Context.MODE_PRIVATE)

    private val _isMemoryEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_MEMORY_ENABLED, true)
    )
    val isMemoryEnabled: StateFlow<Boolean> = _isMemoryEnabled.asStateFlow()

    fun setMemoryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MEMORY_ENABLED, enabled).apply()
        _isMemoryEnabled.value = enabled
    }

    companion object {
        private const val KEY_MEMORY_ENABLED = "key_memory_enabled"
    }
}
