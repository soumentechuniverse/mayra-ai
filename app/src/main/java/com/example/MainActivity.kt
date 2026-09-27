package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.MayraDatabase
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.service.GeminiService
import com.example.ui.screens.ChatScreen
import com.example.ui.theme.MayraAITheme
import com.example.ui.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val database = remember { MayraDatabase.getDatabase(applicationContext) }
            val aiService = remember { GeminiService() }
            val repository = remember {
                ChatRepositoryImpl(
                    aiService = aiService,
                    conversationDao = database.conversationDao()
                )
            }

            val viewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.provideFactory(repository)
            )

            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            MayraAITheme(darkTheme = uiState.isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChatScreen(
                        state = uiState,
                        onEvent = viewModel::onEvent
                    )
                }
            }
        }
    }
}
