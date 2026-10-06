package com.example.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TextToSpeechHelper(context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    try {
                        tts?.language = Locale.getDefault()
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {
            tts = null
        }
    }

    fun speak(text: String, onFinished: (() -> Unit)? = null) {
        if (!isInitialized || tts == null) return

        stop()
        _isSpeaking.value = true

        // Clean markdown symbols for natural TTS speech
        val cleanText = text
            .replace(Regex("```[\\s\\S]*?```"), "Code block omitted.")
            .replace(Regex("[#*`_~]"), "")
            .trim()

        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "MayraTTS"

        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "MayraTTS")
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
