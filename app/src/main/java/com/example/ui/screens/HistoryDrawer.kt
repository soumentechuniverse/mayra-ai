package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Conversation
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraDarkSurface
import com.example.ui.theme.MayraDarkSurfaceBorder
import com.example.ui.theme.MayraDarkSurfaceElevated
import com.example.ui.theme.MayraRose
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDrawer(
    isOpen: Boolean,
    activeConversationId: String?,
    conversations: List<Conversation>,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    onSearchQueryChanged: (String) -> Unit = {},
    showArchived: Boolean = false,
    onToggleArchivedFilter: (Boolean) -> Unit = {},
    onPinConversation: (String) -> Unit = {},
    onArchiveConversation: (String) -> Unit = {},
    onRenameConversation: (String, String) -> Unit = { _, _ -> }
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local dialog states for Rename and Delete Confirmation
    var renamingConversation by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deletingConversation by remember { mutableStateOf<Conversation?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MayraDarkSurface,
        modifier = modifier.testTag("history_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = MayraCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Conversations",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close history",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Start New Chat Action Button
            Button(
                onClick = {
                    onNewChat()
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MayraCyan,
                    contentColor = Color(0xFF003548)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_new_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Conversation",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Local Search Bar (titles & messages, Unicode/multilingual)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = {
                    Text(
                        text = "Search chats & messages...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search icon",
                        tint = MayraCyan,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MayraDarkSurfaceElevated,
                    unfocusedContainerColor = MayraDarkSurfaceElevated,
                    focusedBorderColor = MayraCyan,
                    unfocusedBorderColor = MayraDarkSurfaceBorder,
                    cursorColor = MayraCyan
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips: Chats / Archived
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !showArchived,
                    onClick = { onToggleArchivedFilter(false) },
                    label = { Text("Active Chats") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MayraCyan.copy(alpha = 0.2f),
                        selectedLabelColor = MayraCyan,
                        containerColor = MayraDarkSurfaceElevated,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = MayraDarkSurfaceBorder,
                        selectedBorderColor = MayraCyan,
                        enabled = true,
                        selected = !showArchived
                    ),
                    modifier = Modifier.testTag("history_filter_active")
                )

                FilterChip(
                    selected = showArchived,
                    onClick = { onToggleArchivedFilter(true) },
                    label = { Text("Archived") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MayraCyan.copy(alpha = 0.2f),
                        selectedLabelColor = MayraCyan,
                        containerColor = MayraDarkSurfaceElevated,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = MayraDarkSurfaceBorder,
                        selectedBorderColor = MayraCyan,
                        enabled = true,
                        selected = showArchived
                    ),
                    modifier = Modifier.testTag("history_filter_archived")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp)
                        .testTag("no_conversations_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            searchQuery.isNotBlank() -> "No conversations found"
                            showArchived -> "No archived conversations"
                            else -> "No saved conversations yet"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(conversations, key = { it.id }) { conv ->
                        val isActive = conv.id == activeConversationId
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isActive) MayraDarkSurfaceElevated else MayraDarkSurface
                                )
                                .border(
                                    width = if (isActive) 1.dp else 0.5.dp,
                                    color = if (isActive) MayraCyan else MayraDarkSurfaceBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onSelectConversation(conv.id)
                                    onDismiss()
                                }
                                .padding(12.dp)
                                .testTag("conversation_item_${conv.id}")
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = if (isActive) MayraCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )

                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = conv.title,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1
                                                )
                                                if (conv.isPinned) {
                                                    Icon(
                                                        imageVector = Icons.Filled.PushPin,
                                                        contentDescription = "Pinned",
                                                        tint = MayraCyan,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = formatHistoryDate(conv.updatedAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Action buttons: Pin, Rename, Archive, Delete
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Pin / Unpin
                                        IconButton(
                                            onClick = { onPinConversation(conv.id) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("pin_conversation_${conv.id}")
                                        ) {
                                            Icon(
                                                imageVector = if (conv.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                                contentDescription = if (conv.isPinned) "Unpin conversation" else "Pin conversation",
                                                tint = if (conv.isPinned) MayraCyan else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Rename
                                        IconButton(
                                            onClick = {
                                                renamingConversation = conv
                                                renameText = conv.title
                                            },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("rename_conversation_${conv.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Edit,
                                                contentDescription = "Rename conversation",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Archive / Unarchive
                                        IconButton(
                                            onClick = { onArchiveConversation(conv.id) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("archive_conversation_${conv.id}")
                                        ) {
                                            Icon(
                                                imageVector = if (conv.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                                contentDescription = if (conv.isArchived) "Unarchive" else "Archive",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Delete with confirmation
                                        IconButton(
                                            onClick = { deletingConversation = conv },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("delete_conversation_${conv.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = "Delete conversation",
                                                tint = MayraRose.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                if (conv.preview.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = conv.preview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Rename Conversation Dialog
    renamingConversation?.let { conv ->
        AlertDialog(
            onDismissRequest = { renamingConversation = null },
            title = {
                Text(
                    text = "Rename Conversation",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MayraCyan,
                        cursorColor = MayraCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_text_field")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isNotEmpty()) {
                            onRenameConversation(conv.id, trimmed)
                        }
                        renamingConversation = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MayraCyan,
                        contentColor = Color(0xFF003548)
                    ),
                    modifier = Modifier.testTag("confirm_rename_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { renamingConversation = null },
                    modifier = Modifier.testTag("cancel_rename_button")
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MayraDarkSurfaceElevated,
            modifier = Modifier.testTag("rename_dialog")
        )
    }

    // Delete Confirmation Dialog
    deletingConversation?.let { conv ->
        AlertDialog(
            onDismissRequest = { deletingConversation = null },
            title = {
                Text(
                    text = "Delete Conversation?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${conv.title}\"? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteConversation(conv.id)
                        deletingConversation = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MayraRose,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_delete_conversation_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingConversation = null },
                    modifier = Modifier.testTag("cancel_delete_conversation_button")
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MayraDarkSurfaceElevated,
            modifier = Modifier.testTag("delete_conversation_dialog")
        )
    }
}

private fun formatHistoryDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
