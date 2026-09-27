package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.model.VoiceLanguage
import com.example.domain.model.VoiceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight, persistent preferences manager for Mayra AI voice configurations.
 */
class VoicePreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "mayra_voice_prefs"
        private const val KEY_INPUT_LANG = "voice_input_language"
        private const val KEY_AUTO_SPEAK = "voice_auto_speak"
        private const val KEY_OUTPUT_LANG = "voice_output_language"
    }

    private val sharedPrefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<VoiceSettings> = _settings.asStateFlow()

    private fun loadSettings(): VoiceSettings {
        val inputTag = sharedPrefs.getString(KEY_INPUT_LANG, VoiceLanguage.AUTO.tag)
        val autoSpeak = sharedPrefs.getBoolean(KEY_AUTO_SPEAK, false)
        val outputTag = sharedPrefs.getString(KEY_OUTPUT_LANG, VoiceLanguage.AUTO.tag)

        return VoiceSettings(
            inputLanguage = VoiceLanguage.fromTag(inputTag),
            autoSpeakOutput = autoSpeak,
            outputLanguage = VoiceLanguage.fromTag(outputTag)
        )
    }

    fun updateInputLanguage(language: VoiceLanguage) {
        sharedPrefs.edit().putString(KEY_INPUT_LANG, language.tag).apply()
        _settings.value = _settings.value.copy(inputLanguage = language)
    }

    fun updateAutoSpeak(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_AUTO_SPEAK, enabled).apply()
        _settings.value = _settings.value.copy(autoSpeakOutput = enabled)
    }

    fun updateOutputLanguage(language: VoiceLanguage) {
        sharedPrefs.edit().putString(KEY_OUTPUT_LANG, language.tag).apply()
        _settings.value = _settings.value.copy(outputLanguage = language)
    }
}
