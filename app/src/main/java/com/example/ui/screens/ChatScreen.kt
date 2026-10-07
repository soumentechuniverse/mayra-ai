package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.service.AppUpdateInfo
import com.example.data.service.AppUpdateService
import com.example.ui.components.ChatInputBar
import com.example.ui.components.ChatMessageItem
import com.example.ui.components.HistoryDrawer
import com.example.ui.components.ThinkingIndicator
import com.example.ui.components.WelcomeScreen
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraDarkBackground
import com.example.ui.theme.MayraDarkSurface
import com.example.ui.theme.MayraIndigo
import com.example.ui.theme.MayraTextPrimary
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSettingsOpen by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }

    val updateService = remember { AppUpdateService() }

    LaunchedEffect(
        uiState.messages.size,
        uiState.messages.lastOrNull()?.content
    ) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MayraDarkBackground
            ) {
                HistoryDrawer(
                    conversations = uiState.conversations,
                    archivedConversations = uiState.archivedConversations,
                    selectedConversationId = uiState.currentConversationId,

                    onSelectConversation = { id ->
                        viewModel.selectConversation(id)
                        scope.launch {
                            drawerState.close()
                        }
                    },

                    onNewChat = {
                        viewModel.newChat()
                        scope.launch {
                            drawerState.close()
                        }
                    },

                    onRenameConversation = { id, title ->
                        viewModel.renameConversation(id, title)
                    },

                    onTogglePin = { id, pin ->
                        viewModel.togglePinConversation(id, pin)
                    },

                    onToggleArchive = { id, archived ->
                        viewModel.toggleArchiveConversation(id, archived)
                    },

                    onDeleteConversation = { id ->
                        viewModel.deleteConversation(id)
                    }
                )
            }
        }
    ) {

        Scaffold(
            modifier = modifier.fillMaxSize(),

            topBar = {
                TopAppBar(

                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    drawerState.open()
                                }
                            },
                            modifier = Modifier.testTag("menu_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Chat history",
                                tint = MayraTextPrimary
                            )
                        }
                    },

                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MayraIndigo),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Mayra",
                                    tint = MayraCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(
                                modifier = Modifier.width(10.dp)
                            )

                            Column {

                                Text(
                                    text = uiState.currentConversationTitle,
                                    color = MayraTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )

                                Text(
                                    text = "Mayra AI",
                                    color = MayraCyan,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    },

                    actions = {

                        IconButton(
                            onClick = {
                                isSettingsOpen = true
                            },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MayraCyan
                            )
                        }

                        IconButton(
                            onClick = {
                                viewModel.newChat()
                            },
                            modifier = Modifier.testTag("top_new_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New chat",
                                tint = MayraCyan
                            )
                        }
                    },

                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MayraDarkSurface
                    )
                )
            },

            bottomBar = {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MayraDarkBackground)
                        .navigationBarsPadding()
                ) {

                    ChatInputBar(
                        inputText = uiState.inputText,

                        onInputTextChanged = {
                            viewModel.onInputTextChanged(it)
                        },

                        onSendMessage = {
                            viewModel.sendMessage()
                        },

                        onStopGeneration = {
                            viewModel.stopGeneration()
                        },

                        isGenerating = uiState.isGenerating,

                        attachedFile = uiState.attachedFile,

                        onAttachmentSelected = {
                            viewModel.onAttachmentSelected(it)
                        },

                        speechRecognizerHelper =
                            viewModel.speechRecognizerHelper
                    )
                }
            },

            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState
                )
            },

            containerColor = MayraDarkBackground

        ) { paddingValues ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {

                if (uiState.messages.isEmpty()) {

                    WelcomeScreen(
                        onSelectPrompt = { prompt ->
                            viewModel.onInputTextChanged(prompt)
                            viewModel.sendMessage()
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                } else {

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 8.dp)
                    ) {

                        items(
                            items = uiState.messages,
                            key = { message ->
                                message.id
                            }
                        ) { message ->

                            ChatMessageItem(
                                message = message,

                                onRetry = {
                                    viewModel.retryMessage(it)
                                },

                                onSpeak = {
                                    viewModel.speakText(it)
                                }
                            )
                        }

                        if (
                            uiState.isThinking &&
                            uiState.messages.none {
                                it.status == "SENDING"
                            }
                        ) {

                            item {

                                Box(
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 12.dp
                                    )
                                ) {
                                    ThinkingIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /*
     * SETTINGS DIALOG
     */
    if (isSettingsOpen) {

        AlertDialog(
            onDismissRequest = {
                if (!isCheckingUpdate) {
                    isSettingsOpen = false
                }
            },

            title = {
                Text(
                    text = "Settings",
                    fontWeight = FontWeight.Bold
                )
            },

            text = {

                Column {

                    Text(
                        text = "Mayra AI",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )

                    Spacer(
                        modifier = Modifier.size(8.dp)
                    )

                    Text(
                        text = "App version: ${updateInfo?.currentVersion ?: "1.0.1"}",
                        fontSize = 14.sp
                    )

                    Spacer(
                        modifier = Modifier.size(16.dp)
                    )

                    Text(
                        text = when {
                            isCheckingUpdate ->
                                "Checking for updates..."

                            updateError != null ->
                                updateError!!

                            updateInfo?.isUpdateAvailable == true ->
                                "New version available: ${updateInfo?.latestVersion}"

                            updateInfo != null ->
                                "You are using the latest version."

                            else ->
                                "Check whether a newer version of Mayra AI is available."
                        },
                        fontSize = 14.sp
                    )

                    if (
                        updateInfo?.isUpdateAvailable == true &&
                        updateInfo?.releaseNotes?.isNotBlank() == true
                    ) {

                        Spacer(
                            modifier = Modifier.size(12.dp)
                        )

                        Text(
                            text = updateInfo?.releaseNotes ?: "",
                            fontSize = 13.sp
                        )
                    }
                }
            },

            confirmButton = {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    if (
                        updateInfo?.isUpdateAvailable == true &&
                        updateInfo?.downloadUrl?.isNotBlank() == true
                    ) {

                        Button(
                            onClick = {
                                updateInfo?.let { info ->
                                    updateService.openDownloadUrl(
                                        context = androidx.compose.ui.platform.LocalContext.current,
                                        downloadUrl = info.downloadUrl
                                    )
                                }
                            },
                            enabled = !isCheckingUpdate
                        ) {
                            Text("Update")
                        }

                        Spacer(
                            modifier = Modifier.width(8.dp)
                        )
                    }

                    Button(
                        onClick = {

                            isCheckingUpdate = true
                            updateError = null

                            scope.launch {

                                val result =
                                    updateService.checkForUpdate()

                                result
                                    .onSuccess { info ->
                                        updateInfo = info
                                    }
                                    .onFailure { error ->
                                        updateError =
                                            error.message
                                                ?: "Unable to check for updates."
                                    }

                                isCheckingUpdate = false
                            }
                        },
                        enabled = !isCheckingUpdate
                    ) {
                        Text(
                            if (isCheckingUpdate)
                                "Checking..."
                            else
                                "Check for Update"
                        )
                    }
                }
            },

            dismissButton = {

                Button(
                    onClick = {
                        isSettingsOpen = false
                    },
                    enabled = !isCheckingUpdate
                ) {
                    Text("Close")
                }
            }
        )
    }
}
