package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.domain.model.Attachment
import com.example.domain.model.VoiceState
import com.example.ui.components.AttachmentBottomSheet
import com.example.ui.components.ChatMessageItem
import com.example.ui.components.EmptyChatView
import com.example.ui.components.ErrorBanner
import com.example.ui.components.MayraHeader
import com.example.ui.components.MessageComposer
import com.example.ui.components.ThinkingIndicator
import com.example.ui.viewmodel.ChatUiEvent
import com.example.ui.viewmodel.ChatUiState
import com.example.util.AttachmentHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    state: ChatUiState,
    onEvent: (ChatUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun processUris(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        onEvent(ChatUiEvent.SetAttachmentProcessing(true))
        coroutineScope.launch(Dispatchers.IO) {
            val successful = mutableListOf<Attachment>()
            val errors = mutableListOf<String>()

            for (uri in uris) {
                val res = AttachmentHelper.processUri(context, uri)
                if (res.isSuccess) {
                    successful.add(res.getOrThrow())
                } else {
                    val err = res.exceptionOrNull()?.localizedMessage ?: "Failed to process file."
                    errors.add(err)
                }
            }

            onEvent(ChatUiEvent.SetAttachmentProcessing(false))
            if (successful.isNotEmpty()) {
                onEvent(ChatUiEvent.AttachmentsSelected(successful))
            }
            if (errors.isNotEmpty()) {
                onEvent(ChatUiEvent.AttachmentError(errors.joinToString("\n")))
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        processUris(uris)
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        processUris(uris)
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        processUris(uris)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onEvent(ChatUiEvent.StartVoiceInput)
        } else {
            onEvent(ChatUiEvent.VoicePermissionDenied)
        }
    }

    var previousMessageCount by remember { mutableIntStateOf(state.messages.size) }

    // Auto-scroll to bottom only when appropriate (e.g. user sends message, or is already near bottom)
    LaunchedEffect(state.messages.size, state.isGenerating, state.messages.lastOrNull()?.content?.length) {
        val count = state.messages.size
        if (count == 0) return@LaunchedEffect

        val isNewMessageAdded = count > previousMessageCount
        previousMessageCount = count

        if (isNewMessageAdded) {
            // New message added: smoothly scroll to bottom
            listState.animateScrollToItem(count - 1)
        } else if (state.isGenerating) {
            // During token streaming, only scroll if the user is already near the bottom
            // Do not interrupt the user if they scrolled up to read older messages
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0 && lastVisible >= total - 3) {
                listState.scrollToItem(total - 1)
            }
        }
    }

    // Display transient snackbars for state updates
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onEvent(ChatUiEvent.DismissSnackbar(msg))
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            MayraHeader(
                activeModel = state.selectedModel,
                isGenerating = state.isGenerating,
                onNewChat = { onEvent(ChatUiEvent.NewChatClicked) },
                onOpenHistory = { onEvent(ChatUiEvent.OpenHistory) },
                onOpenSettings = { onEvent(ChatUiEvent.OpenSettings) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Subtle, elegant "Soumen Mondal" watermark in background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("chat_watermark_layer"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Soumen Mondal",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp,
                            fontSize = 28.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                        modifier = Modifier.testTag("soumen_mondal_watermark")
                    )
                }

                Column(
                    modifier = Modifier.fillMaxSize()
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
                        // Empty state greeting with clean welcome
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
                                    },
                                    voiceState = state.voiceState,
                                    onSpeak = { onEvent(ChatUiEvent.SpeakMessage(msg.id, msg.content)) },
                                    onPauseSpeech = { onEvent(ChatUiEvent.PauseSpeech) },
                                    onResumeSpeech = { onEvent(ChatUiEvent.ResumeSpeech) },
                                    onStopSpeech = { onEvent(ChatUiEvent.StopSpeech) }
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
                                        ThinkingIndicator(searchPhase = state.searchPhase)
                                    }
                                }
                            }

                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .testTag("conversation_footer_watermark"),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Mayra AI • Soumen Mondal",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = 1.2.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    )
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }

            // Fixed at bottom directly above the keyboard
            MessageComposer(
                text = state.inputText,
                isGenerating = state.isGenerating,
                onTextChanged = { onEvent(ChatUiEvent.InputTextChanged(it)) },
                onSend = { onEvent(ChatUiEvent.SendClicked) },
                onAttachmentClicked = { onEvent(ChatUiEvent.OpenAttachmentPicker) },
                onVoiceClicked = {
                    if (state.voiceState is VoiceState.Listening) {
                        onEvent(ChatUiEvent.StopVoiceInput)
                    } else {
                        val hasMicPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasMicPermission) {
                            onEvent(ChatUiEvent.StartVoiceInput)
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                voiceState = state.voiceState,
                onCancelVoice = { onEvent(ChatUiEvent.CancelVoiceInput) },
                onStopGenerating = { onEvent(ChatUiEvent.StopGeneration) },
                pendingAttachments = state.pendingAttachments,
                onRemoveAttachment = { onEvent(ChatUiEvent.RemovePendingAttachment(it)) },
                isProcessingAttachments = state.isProcessingAttachment
            )
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
        onDismiss = { onEvent(ChatUiEvent.CloseSettings) },
        searchMode = state.searchMode,
        onSearchModeChanged = { onEvent(ChatUiEvent.SearchModeChanged(it)) },
        voiceSettings = state.voiceSettings,
        onVoiceInputLanguageChanged = { onEvent(ChatUiEvent.VoiceInputLanguageChanged(it)) },
        onVoiceAutoSpeakToggled = { onEvent(ChatUiEvent.VoiceAutoSpeakToggled(it)) },
        onVoiceOutputLanguageChanged = { onEvent(ChatUiEvent.VoiceOutputLanguageChanged(it)) },
        isMemoryEnabled = state.isMemoryEnabled,
        onToggleMemoryEnabled = { onEvent(ChatUiEvent.ToggleMemoryEnabled(it)) },
        memories = state.memories,
        isManageMemoryOpen = state.isManageMemoryOpen,
        onOpenManageMemory = { onEvent(ChatUiEvent.OpenManageMemory) },
        onCloseManageMemory = { onEvent(ChatUiEvent.CloseManageMemory) },
        onAddMemory = { content, cat -> onEvent(ChatUiEvent.AddMemory(content, cat)) },
        onToggleMemoryItem = { id, enabled -> onEvent(ChatUiEvent.ToggleMemoryItem(id, enabled)) },
        onDeleteMemory = { onEvent(ChatUiEvent.DeleteMemoryItem(it)) },
        onClearAllMemories = { onEvent(ChatUiEvent.ConfirmClearMemories) },
        isCheckingUpdate = state.isCheckingUpdate,
        updateInfo = state.updateInfo,
        updateStatusMessage = state.updateStatusMessage,
        updateSourceUrl = state.updateSourceUrl,
        isUpdateSourceConfigOpen = state.isUpdateSourceConfigOpen,
        onCheckForUpdate = { onEvent(ChatUiEvent.CheckForAppUpdate) },
        onUpdateSourceUrlChanged = { onEvent(ChatUiEvent.UpdateSourceUrlChanged(it)) },
        onResetUpdateSourceUrl = { onEvent(ChatUiEvent.ResetUpdateSourceUrl) },
        onToggleUpdateSourceConfig = { onEvent(ChatUiEvent.ToggleUpdateSourceConfig) },
        onDownloadAppUpdate = { ctx, url -> onEvent(ChatUiEvent.DownloadAppUpdate(ctx, url)) },
        onDismissUpdateDialog = { onEvent(ChatUiEvent.DismissUpdateDialog) }
    )

    // Modal History Sheet Architecture
    HistoryDrawer(
        isOpen = state.isHistoryOpen,
        activeConversationId = state.conversation?.id,
        conversations = state.filteredConversations,
        totalConversationCount = state.allConversations.size,
        onSelectConversation = { onEvent(ChatUiEvent.SelectConversation(it)) },
        onDeleteConversation = { onEvent(ChatUiEvent.DeleteConversation(it)) },
        onNewChat = { onEvent(ChatUiEvent.NewChatClicked) },
        onDismiss = { onEvent(ChatUiEvent.CloseHistory) },
        searchQuery = state.historySearchQuery,
        onSearchQueryChanged = { onEvent(ChatUiEvent.HistorySearchQueryChanged(it)) },
        dateFilter = state.historyDateFilter,
        customDateEpoch = state.historyCustomDateEpoch,
        onDateFilterChanged = { filter, epoch -> onEvent(ChatUiEvent.HistoryDateFilterChanged(filter, epoch)) },
        onClearFilters = { onEvent(ChatUiEvent.ClearHistoryFilters) },
        showArchived = state.showArchivedInHistory,
        onToggleArchivedFilter = { onEvent(ChatUiEvent.ToggleHistoryArchivedFilter(it)) },
        onPinConversation = { onEvent(ChatUiEvent.TogglePinConversation(it)) },
        onArchiveConversation = { onEvent(ChatUiEvent.ToggleArchiveConversation(it)) },
        onRenameConversation = { id, title -> onEvent(ChatUiEvent.RenameConversation(id, title)) }
    )

    // Modal Attachment Sheet Architecture
    AttachmentBottomSheet(
        isOpen = state.isAttachmentPickerOpen,
        onDismiss = { onEvent(ChatUiEvent.CloseAttachmentPicker) },
        onPickImages = {
            onEvent(ChatUiEvent.CloseAttachmentPicker)
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onPickPdf = {
            onEvent(ChatUiEvent.CloseAttachmentPicker)
            pdfPickerLauncher.launch(arrayOf("application/pdf"))
        },
        onPickDocs = {
            onEvent(ChatUiEvent.CloseAttachmentPicker)
            docPickerLauncher.launch(
                arrayOf(
                    "text/*",
                    "application/json",
                    "text/plain",
                    "text/csv",
                    "text/markdown",
                    "*/*"
                )
            )
        }
    )
}
