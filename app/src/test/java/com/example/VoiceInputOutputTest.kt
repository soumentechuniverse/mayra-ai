package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.VoicePreferences
import com.example.domain.model.AiModelConfig
import com.example.domain.model.ChatMessage
import com.example.domain.model.Conversation
import com.example.domain.model.MessageRole
import com.example.domain.model.SearchPhase
import com.example.domain.model.VoiceLanguage
import com.example.domain.model.VoiceSettings
import com.example.domain.model.VoiceState
import com.example.domain.service.SpeechRecognizerService
import com.example.domain.service.TextToSpeechService
import com.example.ui.viewmodel.ChatUiEvent
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceInputOutputTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `voice language from tag maps correctly and defaults to auto`() {
        assertEquals(VoiceLanguage.BENGALI, VoiceLanguage.fromTag("bn-BD"))
        assertEquals(VoiceLanguage.ENGLISH, VoiceLanguage.fromTag("en-US"))
        assertEquals(VoiceLanguage.HINDI, VoiceLanguage.fromTag("hi-IN"))
        assertEquals(VoiceLanguage.ARABIC, VoiceLanguage.fromTag("ar-SA"))
        assertEquals(VoiceLanguage.AUTO, VoiceLanguage.fromTag("unknown-tag"))
        assertEquals(VoiceLanguage.AUTO, VoiceLanguage.fromTag(null))
    }

    @Test
    fun `voice preferences correctly persist and update settings`() {
        val prefs = VoicePreferences(context)
        assertEquals(VoiceLanguage.AUTO, prefs.settings.value.inputLanguage)
        assertFalse(prefs.settings.value.autoSpeakOutput)

        prefs.updateInputLanguage(VoiceLanguage.BENGALI)
        assertEquals(VoiceLanguage.BENGALI, prefs.settings.value.inputLanguage)

        prefs.updateAutoSpeak(true)
        assertTrue(prefs.settings.value.autoSpeakOutput)

        prefs.updateOutputLanguage(VoiceLanguage.HINDI)
        assertEquals(VoiceLanguage.HINDI, prefs.settings.value.outputLanguage)
    }

    @Test
    fun `fake speech recognizer transitions through listening, partial results and completion`() {
        val fakeSpeech = FakeSpeechRecognizerService()
        assertEquals(VoiceState.Idle, fakeSpeech.state.value)

        var recognizedText: String? = null
        fakeSpeech.startListening(VoiceLanguage.BENGALI) { text ->
            recognizedText = text
        }
        assertTrue(fakeSpeech.state.value is VoiceState.Listening)

        fakeSpeech.simulatePartial("আজকের আবহাওয়া")
        val current = fakeSpeech.state.value as VoiceState.Listening
        assertEquals("আজকের আবহাওয়া", current.partialText)

        fakeSpeech.simulateFinal("আজকের আবহাওয়া কেমন?")
        assertEquals("আজকের আবহাওয়া কেমন?", recognizedText)
        assertEquals(VoiceState.Idle, fakeSpeech.state.value)
    }

    @Test
    fun `fake speech recognizer handles cancellation and errors gracefully`() {
        val fakeSpeech = FakeSpeechRecognizerService()
        fakeSpeech.startListening(VoiceLanguage.ENGLISH) {}
        assertTrue(fakeSpeech.state.value is VoiceState.Listening)

        fakeSpeech.cancelListening()
        assertEquals(VoiceState.Idle, fakeSpeech.state.value)

        fakeSpeech.startListening(VoiceLanguage.ENGLISH) {}
        fakeSpeech.simulateError("Audio recording error")
        assertTrue(fakeSpeech.state.value is VoiceState.Error)
        assertEquals("Audio recording error", (fakeSpeech.state.value as VoiceState.Error).message)
    }

    @Test
    fun `fake tts handles speak, pause, resume, and stop`() {
        val fakeTts = FakeTextToSpeechService()
        assertEquals(VoiceState.Idle, fakeTts.state.value)

        fakeTts.speak("msg-1", "Hello from Mayra AI", VoiceLanguage.ENGLISH)
        assertTrue(fakeTts.state.value is VoiceState.Speaking)
        val speaking = fakeTts.state.value as VoiceState.Speaking
        assertEquals("msg-1", speaking.messageId)
        assertEquals("Hello from Mayra AI", speaking.text)
        assertFalse(speaking.isPaused)

        fakeTts.pause()
        assertTrue((fakeTts.state.value as VoiceState.Speaking).isPaused)

        fakeTts.resume()
        assertFalse((fakeTts.state.value as VoiceState.Speaking).isPaused)

        fakeTts.stop()
        assertEquals(VoiceState.Idle, fakeTts.state.value)
    }

    @Test
    fun `chat view model integrates speech recognition and populates composer for editing`() = runTest(testDispatcher) {
        val fakeRepo = FakeChatRepository()
        val fakeSpeech = FakeSpeechRecognizerService()
        val fakeTts = FakeTextToSpeechService()
        val prefs = VoicePreferences(context)

        val viewModel = ChatViewModel(
            repository = fakeRepo,
            speechRecognizerService = fakeSpeech,
            textToSpeechService = fakeTts,
            voicePreferences = prefs
        )

        advanceUntilIdle()

        // Trigger voice input
        viewModel.onEvent(ChatUiEvent.StartVoiceInput)
        assertTrue(fakeSpeech.state.value is VoiceState.Listening)

        // Deliver recognized speech text
        fakeSpeech.simulateFinal("What is the latest Android version?")
        advanceUntilIdle()

        // Verify recognized text was placed into the composer input field for user review/editing
        assertEquals("What is the latest Android version?", viewModel.uiState.value.inputText)
        assertNotNull(viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun `chat view model handles permission denial without crash`() = runTest(testDispatcher) {
        val fakeRepo = FakeChatRepository()
        val fakeSpeech = FakeSpeechRecognizerService()
        val fakeTts = FakeTextToSpeechService()

        val viewModel = ChatViewModel(
            repository = fakeRepo,
            speechRecognizerService = fakeSpeech,
            textToSpeechService = fakeTts
        )

        viewModel.onEvent(ChatUiEvent.VoicePermissionDenied)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.voiceState is VoiceState.Error)
        assertNotNull(viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun `chat view model speaks message on speak event`() = runTest(testDispatcher) {
        val fakeRepo = FakeChatRepository()
        val fakeSpeech = FakeSpeechRecognizerService()
        val fakeTts = FakeTextToSpeechService()

        val viewModel = ChatViewModel(
            repository = fakeRepo,
            speechRecognizerService = fakeSpeech,
            textToSpeechService = fakeTts
        )

        viewModel.onEvent(ChatUiEvent.SpeakMessage("msg-123", "Mayra AI response"))
        advanceUntilIdle()

        assertTrue(fakeTts.state.value is VoiceState.Speaking)
        val state = fakeTts.state.value as VoiceState.Speaking
        assertEquals("msg-123", state.messageId)
        assertEquals("Mayra AI response", state.text)
    }

    // --- Fake Test Implementations ---

    private class FakeSpeechRecognizerService : SpeechRecognizerService {
        private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
        override val state: StateFlow<VoiceState> = _state.asStateFlow()

        private var callback: ((String) -> Unit)? = null

        override fun isRecognitionAvailable(): Boolean = true

        override fun startListening(language: VoiceLanguage, onTextRecognized: (String) -> Unit) {
            callback = onTextRecognized
            _state.value = VoiceState.Listening()
        }

        fun simulatePartial(text: String) {
            _state.value = VoiceState.Listening(partialText = text, rmsDb = 3.5f)
        }

        fun simulateFinal(text: String) {
            _state.value = VoiceState.Processing
            callback?.invoke(text)
            _state.value = VoiceState.Idle
        }

        fun simulateError(error: String) {
            _state.value = VoiceState.Error(error)
        }

        override fun stopListening() {
            _state.value = VoiceState.Idle
        }

        override fun cancelListening() {
            _state.value = VoiceState.Idle
        }

        override fun destroy() {
            _state.value = VoiceState.Idle
        }
    }

    private class FakeTextToSpeechService : TextToSpeechService {
        private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
        override val state: StateFlow<VoiceState> = _state.asStateFlow()

        override fun isLanguageAvailable(language: VoiceLanguage): Boolean = true

        override fun speak(messageId: String, text: String, language: VoiceLanguage) {
            _state.value = VoiceState.Speaking(messageId, text, isPaused = false)
        }

        override fun pause() {
            val current = _state.value
            if (current is VoiceState.Speaking) {
                _state.value = current.copy(isPaused = true)
            }
        }

        override fun resume() {
            val current = _state.value
            if (current is VoiceState.Speaking) {
                _state.value = current.copy(isPaused = false)
            }
        }

        override fun stop() {
            _state.value = VoiceState.Idle
        }

        override fun destroy() {
            _state.value = VoiceState.Idle
        }
    }

    private class FakeChatRepository : com.example.data.repository.ChatRepository {
        val initialConv = Conversation(id = "conv-1", title = "New Chat", createdAt = 0L, updatedAt = 0L)
        override val activeConversation = MutableStateFlow<Conversation?>(initialConv)
        override val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
        override val conversations = MutableStateFlow<List<Conversation>>(listOf(initialConv))
        override val memories = MutableStateFlow<List<com.example.domain.model.MemoryItem>>(emptyList())
        override val isMemoryEnabled = MutableStateFlow(true)
        override val isGenerating = MutableStateFlow(false)
        override val searchPhase = MutableStateFlow(SearchPhase.IDLE)

        override suspend fun startNewConversation(title: String): Conversation = initialConv
        override suspend fun selectConversation(conversationId: String) {}
        override suspend fun renameConversation(conversationId: String, newTitle: String) {}
        override suspend fun togglePinConversation(conversationId: String) {}
        override suspend fun toggleArchiveConversation(conversationId: String) {}
        override suspend fun sendMessage(content: String, config: AiModelConfig, attachments: List<com.example.domain.model.Attachment>): Result<ChatMessage> {
            val msg = ChatMessage(conversationId = "conv-1", role = MessageRole.USER, content = content)
            return Result.success(msg)
        }
        override suspend fun retryLastFailed(config: AiModelConfig): Result<ChatMessage> = Result.failure(IllegalStateException("No failed"))
        override suspend fun clearMessages() { messages.value = emptyList() }
        override suspend fun deleteConversation(conversationId: String) {}
        override suspend fun cancelGeneration() {}

        override fun setMemoryEnabled(enabled: Boolean) {
            isMemoryEnabled.value = enabled
        }
        override suspend fun saveMemory(content: String, category: com.example.domain.model.MemoryCategory): com.example.domain.model.MemoryItem {
            val item = com.example.domain.model.MemoryItem(content = content, category = category)
            memories.value = listOf(item) + memories.value
            return item
        }
        override suspend fun toggleMemoryItemEnabled(memoryId: String, enabled: Boolean) {}
        override suspend fun deleteMemory(memoryId: String) {
            memories.value = memories.value.filter { it.id != memoryId }
        }
        override suspend fun clearAllMemories() {
            memories.value = emptyList()
        }
        override suspend fun searchConversations(query: String): List<Conversation> = conversations.value
    }
}
