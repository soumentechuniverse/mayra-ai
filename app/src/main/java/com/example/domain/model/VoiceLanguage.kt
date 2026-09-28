package com.example.domain.model

import java.util.Locale

/**
 * Supported speech recognition and text-to-speech languages for Mayra AI.
 */
enum class VoiceLanguage(
    val tag: String,
    val displayName: String,
    val nativeName: String,
    val locale: Locale?
) {
    AUTO("auto", "Auto Detect", "স্বয়ংক্রিয়", null),
    BENGALI("bn-IN", "Bengali (India)", "বাংলা", Locale("bn", "IN")),
    ENGLISH("en-US", "English", "English", Locale("en", "US")),
    HINDI("hi-IN", "Hindi", "हिन्दी", Locale("hi", "IN")),
    URDU("ur-PK", "Urdu", "اردو", Locale("ur", "PK")),
    ARABIC("ar-SA", "Arabic", "العربية", Locale("ar", "SA")),
    SPANISH("es-ES", "Spanish", "Español", Locale("es", "ES")),
    FRENCH("fr-FR", "French", "Français", Locale("fr", "FR")),
    GERMAN("de-DE", "German", "Deutsch", Locale("de", "DE")),
    CHINESE("zh-CN", "Chinese", "中文", Locale("zh", "CN")),
    JAPANESE("ja-JP", "Japanese", "日本語", Locale("ja", "JP"));

    companion object {
        fun fromTag(tag: String?): VoiceLanguage {
            return entries.find { it.tag.equals(tag, ignoreCase = true) } ?: AUTO
        }
    }
}
