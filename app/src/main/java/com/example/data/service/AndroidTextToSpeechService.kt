package com.example.data.service

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.domain.model.VoiceLanguage
import com.example.domain.model.VoiceState
import com.example.domain.service.TextToSpeechService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Production-ready Android [TextToSpeech] service for Mayra AI.
 * 
 * Supports Bengali, English, Hindi, and multilingual response speech,
 * strips noisy Markdown artifacts before audio generation, isolates errors,
 * and maintains clean lifecycle cleanup without leaking contexts.
 */
class AndroidTextToSpeechService(
    context: Context
) : TextToSpeechService, TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    override val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingSpeakAction: (() -> Unit)? = null

    private var currentSpeakingMessageId: String? = null
    private var currentSpeakingText: String? = null

    init {
        mainHandler.post {
            try {
                tts = TextToSpeech(appContext, this)
            } catch (e: Exception) {
                _state.value = VoiceState.Error("TTS engine could not be initialized.")
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(createProgressListener())
            pendingSpeakAction?.invoke()
            pendingSpeakAction = null
        } else {
            isInitialized = false
            _state.value = VoiceState.Error("Failed to initialize Text-to-Speech engine on this device.")
        }
    }

    override fun isLanguageAvailable(language: VoiceLanguage): Boolean {
        val targetLocale = language.locale ?: Locale.getDefault()
        return try {
            val availability = tts?.isLanguageAvailable(targetLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            availability >= TextToSpeech.LANG_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    override fun speak(
        messageId: String,
        text: String,
        language: VoiceLanguage
    ) {
        val cleanSpeech = cleanMarkdownForSpeech(text)
        if (cleanSpeech.isBlank()) {
            _state.value = VoiceState.Idle
            return
        }

        val speakRunnable: () -> Unit = {
            try {
                val targetLocale = resolveLocale(language, cleanSpeech)
                val langResult = tts?.setLanguage(targetLocale)

                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Gracefully try device default locale
                    tts?.setLanguage(Locale.getDefault())
                }

                currentSpeakingMessageId = messageId
                currentSpeakingText = cleanSpeech
                _state.value = VoiceState.Speaking(messageId = messageId, text = cleanSpeech, isPaused = false)

                val utteranceId = "mayra_utt_${UUID.randomUUID()}"
                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                }

                tts?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            } catch (e: Exception) {
                _state.value = VoiceState.Error(e.localizedMessage ?: "Failed to speak response.")
            }
            Unit
        }

        mainHandler.post {
            if (isInitialized) {
                speakRunnable()
            } else {
                pendingSpeakAction = speakRunnable
            }
        }
    }

    override fun pause() {
        mainHandler.post {
            val current = _state.value
            if (current is VoiceState.Speaking && !current.isPaused) {
                tts?.stop()
                _state.value = current.copy(isPaused = true)
            }
        }
    }

    override fun resume() {
        mainHandler.post {
            val current = _state.value
            if (current is VoiceState.Speaking && current.isPaused) {
                val msgId = currentSpeakingMessageId ?: current.messageId
                val text = currentSpeakingText ?: current.text
                speak(msgId, text, VoiceLanguage.AUTO)
            }
        }
    }

    override fun stop() {
        mainHandler.post {
            try {
                tts?.stop()
            } catch (ignored: Exception) {
            } finally {
                currentSpeakingMessageId = null
                currentSpeakingText = null
                _state.value = VoiceState.Idle
            }
        }
    }

    override fun destroy() {
        mainHandler.post {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (ignored: Exception) {
            } finally {
                tts = null
                isInitialized = false
                currentSpeakingMessageId = null
                currentSpeakingText = null
                _state.value = VoiceState.Idle
            }
        }
    }

    private fun resolveLocale(language: VoiceLanguage, text: String): Locale {
        if (language != VoiceLanguage.AUTO && language.locale != null) {
            return language.locale
        }

        // Automatic detection for Bengali Unicode script range (\u0980-\u09FF)
        val hasBengaliScript = text.any { it in '\u0980'..'\u09FF' }
        if (hasBengaliScript) {
            return Locale("bn", "BD")
        }

        // Automatic detection for Devanagari script range (\u0900-\u097F)
        val hasDevanagariScript = text.any { it in '\u0900'..'\u097F' }
        if (hasDevanagariScript) {
            return Locale("hi", "IN")
        }

        // Automatic detection for Arabic script range (\u0600-\u06FF)
        val hasArabicScript = text.any { it in '\u0600'..'\u06FF' }
        if (hasArabicScript) {
            return Locale("ar", "SA")
        }

        return Locale.getDefault()
    }

    /**
     * Strips Markdown formatting symbols (headers, bold/italic, code blocks, links)
     * so that speech synthesis produces natural, comfortable audio.
     */
    private fun cleanMarkdownForSpeech(raw: String): String {
        return raw
            // Remove code blocks
            .replace(Regex("""```[\s\S]*?```"""), " Code block omitted. ")
            // Remove inline code
            .replace(Regex("""`([^`]+)`"""), "$1")
            // Remove markdown links [text](url) -> text
            .replace(Regex("""\[([^\]]+)\]\([^\)]+\)"""), "$1")
            // Remove image links ![alt](url) -> ""
            .replace(Regex("""!\[[^\]]*\]\([^\)]+\)"""), "")
            // Remove headers #, ##, etc.
            .replace(Regex("""(?m)^#{1,6}\s*"""), "")
            // Remove bold/italics
            .replace(Regex("""\*{1,3}([^*]+)\*{1,3}"""), "$1")
            .replace(Regex("""_{1,3}([^_]+)_{1,3}"""), "$1")
            // Remove bullet points and numbered list markers
            .replace(Regex("""(?m)^[\*\-\+]\s+"""), "")
            .replace(Regex("""(?m)^\d+\.\s+"""), "")
            // Remove blockquotes
            .replace(Regex("""(?m)^>\s*"""), "")
            // Normalize spaces
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun createProgressListener(): UtteranceProgressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            // Keep speaking state active
        }

        override fun onDone(utteranceId: String?) {
            mainHandler.post {
                _state.value = VoiceState.Idle
                currentSpeakingMessageId = null
                currentSpeakingText = null
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            mainHandler.post {
                _state.value = VoiceState.Error("An error occurred during audio synthesis.")
                currentSpeakingMessageId = null
                currentSpeakingText = null
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            mainHandler.post {
                _state.value = VoiceState.Error("Text-to-speech error ($errorCode).")
                currentSpeakingMessageId = null
                currentSpeakingText = null
            }
        }
    }
}
