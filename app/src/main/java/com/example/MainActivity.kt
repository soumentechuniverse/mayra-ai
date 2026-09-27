package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.MayraDatabase
import com.example.data.local.MemoryPreferences
import com.example.data.local.VoicePreferences
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.service.AndroidSpeechRecognizerService
import com.example.data.service.AndroidTextToSpeechService
import com.example.data.service.GeminiService
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.StartupScreen
import com.example.ui.theme.MayraAITheme
import com.example.ui.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    private var speechRecognizerService: AndroidSpeechRecognizerService? = null
    private var textToSpeechService: AndroidTextToSpeechService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val speechService = AndroidSpeechRecognizerService(applicationContext).also {
            speechRecognizerService = it
        }
        val ttsService = AndroidTextToSpeechService(applicationContext).also {
            textToSpeechService = it
        }
        val voicePrefs = VoicePreferences(applicationContext)

        setContent {
            val database = remember { MayraDatabase.getDatabase(applicationContext) }
            val memoryPrefs = remember { MemoryPreferences(applicationContext) }
            val aiService = remember { GeminiService() }
            val repository = remember {
                ChatRepositoryImpl(
                    aiService = aiService,
                    conversationDao = database.conversationDao(),
                    memoryDao = database.memoryDao(),
                    memoryPreferences = memoryPrefs
                )
            }

            val viewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.provideFactory(
                    repository = repository,
                    speechRecognizerService = speechService,
                    textToSpeechService = ttsService,
                    voicePreferences = voicePrefs
                )
            )

            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            var showStartupScreen by rememberSaveable { mutableStateOf(true) }

            MayraAITheme(darkTheme = uiState.isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Crossfade(
                        targetState = showStartupScreen,
                        animationSpec = tween(durationMillis = 600),
                        label = "StartupToChatTransition"
                    ) { isStartup ->
                        if (isStartup) {
                            StartupScreen(
                                onStartupFinished = { showStartupScreen = false }
                            )
                        } else {
                            ChatScreen(
                                state = uiState,
                                onEvent = viewModel::onEvent
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        speechRecognizerService?.cancelListening()
        textToSpeechService?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizerService?.destroy()
        textToSpeechService?.destroy()
    }
}
