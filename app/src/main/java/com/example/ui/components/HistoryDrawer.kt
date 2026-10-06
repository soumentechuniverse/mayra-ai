package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.local.entity.ConversationEntity
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraDarkBackground
import com.example.ui.theme.MayraDarkBorder
import com.example.ui.theme.MayraDarkSurface
import com.example.ui.theme.MayraDarkSurfaceVariant
import com.example.ui.theme.MayraErrorRed
import com.example.ui.theme.MayraIndigo
import com.example.ui.theme.MayraTextMuted
import com.example.ui.theme.MayraTextPrimary
import com.example.ui.theme.MayraTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryDrawer(
    conversations: List<ConversationEntity>,
    archivedConversations: List<ConversationEntity>,
    selectedConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onToggleArchive: (String, Boolean) -> Unit,
    onDeleteConversation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var renameDialogTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameInputText by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    val filteredList = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) {
            conversations
        } else {
            val q = searchQuery.trim().lowercase()
            conversations.filter { conv ->
                val dateStr = dateFormat.format(Date(conv.updatedAt)).lowercase()
                conv.title.lowercase().contains(q) || dateStr.contains(q)
            }
        }
    }

    val pinnedList = filteredList.filter { it.isPinned }
    val regularList = filteredList.filter { !it.isPinned }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(310.dp)
            .background(MayraDarkBackground)
            .padding(16.dp)
    ) {
        // App Header & Branding
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MayraIndigo)
                        .border(1.dp, MayraCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MayraCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Mayra AI",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MayraTextPrimary
                    )
                    Text(
                        text = "Multimodal Intelligence",
                        fontSize = 11.sp,
                        color = MayraCyan
                    )
                }
            }

            // New Chat Button
            Button(
                onClick = onNewChat,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MayraIndigo,
                    contentColor = MayraTextPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("new_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Chat",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("New", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar (Keywords & Date filter)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text("Search chats by keyword or date...", fontSize = 13.sp, color = MayraTextMuted)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MayraCyan,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MayraTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MayraCyan,
                unfocusedBorderColor = MayraDarkBorder,
                focusedContainerColor = MayraDarkSurface,
                unfocusedContainerColor = MayraDarkSurface,
                cursorColor = MayraCyan,
                focusedTextColor = MayraTextPrimary,
                unfocusedTextColor = MayraTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_search_input")
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Conversation List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Pinned Section
            if (pinnedList.isNotEmpty()) {
                item {
                    Text(
                        text = "PINNED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MayraCyan,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                items(pinnedList, key = { it.id }) { conv ->
                    ConversationRow(
                        conversation = conv,
                        isSelected = conv.id == selectedConversationId,
                        onSelect = { onSelectConversation(conv.id) },
                        onRename = {
                            renameDialogTarget = conv
                            renameInputText = conv.title
                        },
                        onTogglePin = { onTogglePin(conv.id, !conv.isPinned) },
                        onToggleArchive = { onToggleArchive(conv.id, !conv.isArchived) },
                        onDelete = { onDeleteConversation(conv.id) },
                        dateFormat = dateFormat
                    )
                }
            }

            // Recent Section
            if (regularList.isNotEmpty()) {
                item {
                    Text(
                        text = "CHATS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MayraTextSecondary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                    )
                }
                items(regularList, key = { it.id }) { conv ->
                    ConversationRow(
                        conversation = conv,
                        isSelected = conv.id == selectedConversationId,
                        onSelect = { onSelectConversation(conv.id) },
                        onRename = {
                            renameDialogTarget = conv
                            renameInputText = conv.title
                        },
                        onTogglePin = { onTogglePin(conv.id, !conv.isPinned) },
                        onToggleArchive = { onToggleArchive(conv.id, !conv.isArchived) },
                        onDelete = { onDeleteConversation(conv.id) },
                        dateFormat = dateFormat
                    )
                }
            }

            if (filteredList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching conversations" else "No conversations yet",
                            color = MayraTextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Archived Toggle
            if (archivedConversations.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showArchived = !showArchived }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = null,
                            tint = MayraTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Archived (${archivedConversations.size})",
                            fontSize = 12.sp,
                            color = MayraTextMuted
                        )
                    }
                }

                if (showArchived) {
                    items(archivedConversations, key = { it.id }) { conv ->
                        ConversationRow(
                            conversation = conv,
                            isSelected = conv.id == selectedConversationId,
                            onSelect = { onSelectConversation(conv.id) },
                            onRename = {
                                renameDialogTarget = conv
                                renameInputText = conv.title
                            },
                            onTogglePin = { onTogglePin(conv.id, !conv.isPinned) },
                            onToggleArchive = { onToggleArchive(conv.id, !conv.isArchived) },
                            onDelete = { onDeleteConversation(conv.id) },
                            dateFormat = dateFormat
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = MayraDarkBorder,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Watermark & Creator Branding
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Created by Soumen Mondal",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MayraCyan
            )
            Text(
                text = "Powered by Gemini 3.8 Flash",
                fontSize = 11.sp,
                color = MayraTextMuted
            )
        }
    }

    // Rename Dialog
    renameDialogTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameDialogTarget = null },
            title = { Text("Rename Chat", color = MayraTextPrimary, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MayraCyan,
                        focusedTextColor = MayraTextPrimary,
                        unfocusedTextColor = MayraTextPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameInputText.isNotBlank()) {
                            onRenameConversation(target.id, renameInputText.trim())
                        }
                        renameDialogTarget = null
                    }
                ) {
                    Text("Save", color = MayraCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogTarget = null }) {
                    Text("Cancel", color = MayraTextMuted)
                }
            },
            containerColor = MayraDarkSurface
        )
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationEntity,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    dateFormat: SimpleDateFormat
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MayraDarkSurfaceVariant else Color.Transparent,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MayraIndigo.copy(alpha = 0.6f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onSelect() }
            .testTag("conversation_item_${conversation.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = if (isSelected) MayraCyan else MayraTextMuted,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.title,
                        color = if (isSelected) MayraTextPrimary else MayraTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (conversation.isPinned) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = MayraCyan,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Text(
                    text = dateFormat.format(Date(conversation.updatedAt)),
                    color = MayraTextMuted,
                    fontSize = 10.sp
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MayraTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(MayraDarkSurface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = MayraTextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MayraCyan, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (conversation.isPinned) "Unpin" else "Pin to top", color = MayraTextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(if (conversation.isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin, contentDescription = null, tint = MayraCyan, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showMenu = false
                            onTogglePin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (conversation.isArchived) "Unarchive" else "Archive", color = MayraTextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(if (conversation.isArchived) Icons.Default.Unarchive else Icons.Default.Archive, contentDescription = null, tint = MayraTextSecondary, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showMenu = false
                            onToggleArchive()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MayraErrorRed, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MayraErrorRed, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
