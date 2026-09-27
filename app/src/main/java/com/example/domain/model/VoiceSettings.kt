package com.example.domain.model

/**
 * Persisted preferences for voice recognition and response speech synthesis.
 */
data class VoiceSettings(
    val inputLanguage: VoiceLanguage = VoiceLanguage.AUTO,
    val autoSpeakOutput: Boolean = false,
    val outputLanguage: VoiceLanguage = VoiceLanguage.AUTO
)
