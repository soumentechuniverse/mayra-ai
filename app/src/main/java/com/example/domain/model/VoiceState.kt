package com.example.domain.model

/**
 * Unified sealed state model for voice interaction (speech-to-text and text-to-speech).
 */
sealed interface VoiceState {
    /**
     * Neither listening nor speaking; microphone and TTS are idle.
     */
    data object Idle : VoiceState

    /**
     * Actively listening to user's speech via the microphone.
     */
    data class Listening(
        val partialText: String = "",
        val rmsDb: Float = 0f
    ) : VoiceState

    /**
     * Processing final recognized audio before injecting into composer.
     */
    data object Processing : VoiceState

    /**
     * Actively synthesizing/speaking an AI assistant response via TTS.
     */
    data class Speaking(
        val messageId: String,
        val text: String,
        val isPaused: Boolean = false
    ) : VoiceState

    /**
     * Non-fatal voice operation error (e.g. mic permission denied, language unavailable).
     */
    data class Error(
        val message: String
    ) : VoiceState
}
