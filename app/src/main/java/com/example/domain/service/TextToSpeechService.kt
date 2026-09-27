package com.example.domain.service

import com.example.domain.model.VoiceLanguage
import com.example.domain.model.VoiceState
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain abstraction for Android Text-to-Speech (TTS) response synthesis.
 */
interface TextToSpeechService {
    val state: StateFlow<VoiceState>

    /**
     * Checks whether the specified language locale is supported by the device's TTS engine.
     */
    fun isLanguageAvailable(language: VoiceLanguage): Boolean

    /**
     * Speaks the assistant response text aloud.
     */
    fun speak(
        messageId: String,
        text: String,
        language: VoiceLanguage
    )

    /**
     * Pauses the currently active speech playback.
     */
    fun pause()

    /**
     * Resumes paused speech playback.
     */
    fun resume()

    /**
     * Stops currently active speech synthesis and resets to Idle.
     */
    fun stop()

    /**
     * Releases TTS resources and shuts down the TTS engine.
     */
    fun destroy()
}
