package com.example.domain.service

import com.example.domain.model.VoiceLanguage
import com.example.domain.model.VoiceState
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain abstraction for Android Speech Recognition.
 */
interface SpeechRecognizerService {
    val state: StateFlow<VoiceState>

    /**
     * Checks if speech recognition service is available on this device.
     */
    fun isRecognitionAvailable(): Boolean

    /**
     * Begins listening to microphone input with the specified target language.
     * @param onTextRecognized Callback invoked when final recognized text is ready
     */
    fun startListening(
        language: VoiceLanguage,
        onTextRecognized: (String) -> Unit
    )

    /**
     * Stops listening and requests final speech results.
     */
    fun stopListening()

    /**
     * Cancels active recognition and resets to Idle without delivering results.
     */
    fun cancelListening()

    /**
     * Releases system speech recognizer resources.
     */
    fun destroy()
}
