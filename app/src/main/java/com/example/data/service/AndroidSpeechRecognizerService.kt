package com.example.data.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.domain.model.VoiceLanguage
import com.example.domain.model.VoiceState
import com.example.domain.service.SpeechRecognizerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Production-ready Android [SpeechRecognizer] service for Mayra AI.
 * 
 * Safely wraps Android speech recognition APIs, handles RMS volume levels,
 * manages partial and final speech results, isolates errors, and avoids memory leaks.
 */
class AndroidSpeechRecognizerService(
    context: Context
) : SpeechRecognizerService {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    override val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var activeCallback: ((String) -> Unit)? = null
    private var isCurrentlyListening = false

    override fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(appContext)
    }

    override fun startListening(
        language: VoiceLanguage,
        onTextRecognized: (String) -> Unit
    ) {
        mainHandler.post {
            try {
                if (!isRecognitionAvailable()) {
                    _state.value = VoiceState.Error("Speech recognition is not available on this device.")
                    return@post
                }

                cancelListeningInternal()

                activeCallback = onTextRecognized
                isCurrentlyListening = true
                _state.value = VoiceState.Listening()

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                    setRecognitionListener(createListener())
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                    if (language != VoiceLanguage.AUTO && language.tag.isNotBlank()) {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.tag)
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(language.tag))
                    }
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                isCurrentlyListening = false
                _state.value = VoiceState.Error(e.localizedMessage ?: "Failed to start speech recognition.")
            }
        }
    }

    override fun stopListening() {
        mainHandler.post {
            try {
                if (isCurrentlyListening) {
                    isCurrentlyListening = false
                    _state.value = VoiceState.Processing
                    speechRecognizer?.stopListening()
                }
            } catch (e: Exception) {
                _state.value = VoiceState.Idle
            }
        }
    }

    override fun cancelListening() {
        mainHandler.post {
            cancelListeningInternal()
            _state.value = VoiceState.Idle
        }
    }

    private fun cancelListeningInternal() {
        isCurrentlyListening = false
        activeCallback = null
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (ignored: Exception) {
        } finally {
            speechRecognizer = null
        }
    }

    override fun destroy() {
        mainHandler.post {
            cancelListeningInternal()
            _state.value = VoiceState.Idle
        }
    }

    private fun createListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (isCurrentlyListening) {
                _state.value = VoiceState.Listening()
            }
        }

        override fun onBeginningOfSpeech() {
            if (isCurrentlyListening) {
                _state.value = VoiceState.Listening()
            }
        }

        override fun onRmsChanged(rmsdB: Float) {
            val current = _state.value
            if (current is VoiceState.Listening) {
                _state.value = current.copy(rmsDb = rmsdB)
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            if (isCurrentlyListening) {
                _state.value = VoiceState.Processing
            }
        }

        override fun onError(error: Int) {
            isCurrentlyListening = false
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check your microphone."
                SpeechRecognizer.ERROR_CLIENT -> "Speech service error. Please try again."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice input."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network connection error during voice recognition."
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap the microphone to try again."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy. Please try again in a moment."
                SpeechRecognizer.ERROR_SERVER -> "Speech server error. Please try again shortly."
                else -> "Speech recognition error ($error)."
            }
            _state.value = VoiceState.Error(message)
            cancelListeningInternal()
        }

        override fun onResults(results: Bundle?) {
            isCurrentlyListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim()

            if (!recognizedText.isNullOrEmpty()) {
                activeCallback?.invoke(recognizedText)
                _state.value = VoiceState.Idle
            } else {
                _state.value = VoiceState.Error("No speech detected. Tap the microphone to try again.")
            }
            cancelListeningInternal()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (isCurrentlyListening) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim()
                if (!partial.isNullOrEmpty()) {
                    val current = _state.value
                    val rms = if (current is VoiceState.Listening) current.rmsDb else 0f
                    _state.value = VoiceState.Listening(partialText = partial, rmsDb = rms)
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
