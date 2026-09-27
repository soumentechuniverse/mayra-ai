package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.components.ChatMessageItem
import com.example.ui.components.EmptyChatView
import com.example.ui.components.ErrorBanner
import com.example.ui.components.MayraHeader
import com.example.ui.components.MessageComposer
import com.example.ui.components.ThinkingIndicator
import com.example.ui.viewmodel.ChatUiEvent
import com.example.ui.viewmodel.ChatUiState

@Composable
fun ChatScreen(
    state: ChatUiState,
    onEvent: (ChatUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Auto-scroll to bottom when new messages are added, response streams, or thinking state changes
    LaunchedEffect(state.messages.size, state.isGenerating, state.messages.lastOrNull()?.content?.length) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    // Display snackbar feedback when triggered
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onEvent(ChatUiEvent.DismissSnackbar(msg))
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        topBar = {
            MayraHeader(
                activeModel = state.selectedModel,
                isGenerating = state.isGenerating,
                onNewChat = { onEvent(ChatUiEvent.NewChatClicked) },
                onOpenHistory = { onEvent(ChatUiEvent.OpenHistory) },
                onOpenSettings = { onEvent(ChatUiEvent.OpenSettings) },
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            MessageComposer(
                text = state.inputText,
                isGenerating = state.isGenerating,
                onTextChanged = { onEvent(ChatUiEvent.InputTextChanged(it)) },
                onSend = { onEvent(ChatUiEvent.SendClicked) },
                onAttachmentClicked = { onEvent(ChatUiEvent.AttachmentPlaceholderClicked) },
                onVoiceClicked = { onEvent(ChatUiEvent.VoicePlaceholderClicked) },
                onStopGenerating = { onEvent(ChatUiEvent.StopGeneration) },
                modifier = Modifier.imePadding()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Dismissible error banner for network/service faults
            state.bannerError?.let { err ->
                ErrorBanner(
                    errorMessage = err,
                    onRetry = { onEvent(ChatUiEvent.RetryLastFailed) },
                    onDismiss = { onEvent(ChatUiEvent.DismissBannerError) }
                )
            }

            if (state.messages.isEmpty()) {
                // Empty state greeting with quick-start suggestion chips
                EmptyChatView(
                    onSuggestionClicked = { onEvent(ChatUiEvent.SuggestionClicked(it)) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Scrollable conversation area
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("chat_messages_list")
                ) {
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    items(state.messages, key = { it.id }) { msg ->
                        ChatMessageItem(
                            message = msg,
                            onRetry = { onEvent(ChatUiEvent.RetryLastFailed) },
                            onCopiedFeedback = {
                                onEvent(ChatUiEvent.DismissSnackbar("Copied to clipboard"))
                            }
                        )
                    }

                    // Show thinking indicator ONLY before the first response chunk arrives
                    val isWaitingForFirstChunk = state.isGenerating && (
                        state.messages.isEmpty() ||
                        state.messages.last().role != com.example.domain.model.MessageRole.ASSISTANT ||
                        state.messages.last().content.isEmpty()
                    )
                    if (isWaitingForFirstChunk) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                ThinkingIndicator()
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // Modal Settings Sheet Architecture
    SettingsSheet(
        isOpen = state.isSettingsOpen,
        selectedModel = state.selectedModel,
        availableModels = state.availableModels,
        isDarkTheme = state.isDarkTheme,
        onModelSelected = { onEvent(ChatUiEvent.ModelSelected(it)) },
        onTemperatureChanged = { onEvent(ChatUiEvent.TemperatureChanged(it)) },
        onToggleTheme = { onEvent(ChatUiEvent.ToggleTheme) },
        onClearChat = { onEvent(ChatUiEvent.ClearCurrentChat) },
        onDismiss = { onEvent(ChatUiEvent.CloseSettings) }
    )

    // Modal History Sheet Architecture
    HistoryDrawer(
        isOpen = state.isHistoryOpen,
        activeConversationId = state.conversation?.id,
        conversations = state.allConversations,
        onSelectConversation = { onEvent(ChatUiEvent.SelectConversation(it)) },
        onDeleteConversation = { onEvent(ChatUiEvent.DeleteConversation(it)) },
        onNewChat = { onEvent(ChatUiEvent.NewChatClicked) },
        onDismiss = { onEvent(ChatUiEvent.CloseHistory) }
    )
}
