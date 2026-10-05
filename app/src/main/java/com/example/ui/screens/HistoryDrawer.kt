package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterListOff
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Conversation
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraDarkSurface
import com.example.ui.theme.MayraDarkSurfaceBorder
import com.example.ui.theme.MayraDarkSurfaceElevated
import com.example.ui.theme.MayraRose
import com.example.util.DateFilterHelper
import com.example.util.HistoryDateFilter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDrawer(
    isOpen: Boolean,
    activeConversationId: String?,
    conversations: List<Conversation>,
    totalConversationCount: Int = conversations.size,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    onSearchQueryChanged: (String) -> Unit = {},
    dateFilter: HistoryDateFilter = HistoryDateFilter.ALL,
    customDateEpoch: Long? = null,
    onDateFilterChanged: (HistoryDateFilter, Long?) -> Unit = { _, _ -> },
    onClearFilters: () -> Unit = {},
    showArchived: Boolean = false,
    onToggleArchivedFilter: (Boolean) -> Unit = {},
    onPinConversation: (String) -> Unit = {},
    onArchiveConversation: (String) -> Unit = {},
    onRenameConversation: (String, String) -> Unit = { _, _ -> }
) {
    if (!isOpen) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local dialog states for Rename and Delete Confirmation
    var renamingConversation by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deletingConversation by remember { mutableStateOf<Conversation?>(null) }

    // DatePicker Dialog Trigger
    val openCalendarPicker = {
        val calendar = Calendar.getInstance()
        if (customDateEpoch != null) {
            calendar.timeInMillis = customDateEpoch
        }
        val dpd = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onDateFilterChanged(HistoryDateFilter.CUSTOM, selectedCal.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        dpd.show()
    }

    val isFilterActive = searchQuery.isNotBlank() || dateFilter != HistoryDateFilter.ALL

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
            // 1. Header Row
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

            // 2. Start New Chat Action Button
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

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Search Bar for filtering by keyword or date
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = {
                    Text(
                        text = "Search by keyword or date...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChanged("") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = openCalendarPicker,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("history_open_datepicker_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = "Pick date filter",
                                tint = if (dateFilter == HistoryDateFilter.CUSTOM) MayraCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
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

            // 4. Date Filter Chips Row
            val dateChipsScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(dateChipsScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = dateFilter == HistoryDateFilter.ALL,
                    onClick = { onDateFilterChanged(HistoryDateFilter.ALL, null) },
                    label = { Text("All Dates") },
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
                        selected = dateFilter == HistoryDateFilter.ALL
                    ),
                    modifier = Modifier.testTag("date_filter_all")
                )

                FilterChip(
                    selected = dateFilter == HistoryDateFilter.TODAY,
                    onClick = { onDateFilterChanged(HistoryDateFilter.TODAY, null) },
                    label = { Text("Today") },
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
                        selected = dateFilter == HistoryDateFilter.TODAY
                    ),
                    modifier = Modifier.testTag("date_filter_today")
                )

                FilterChip(
                    selected = dateFilter == HistoryDateFilter.YESTERDAY,
                    onClick = { onDateFilterChanged(HistoryDateFilter.YESTERDAY, null) },
                    label = { Text("Yesterday") },
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
                        selected = dateFilter == HistoryDateFilter.YESTERDAY
                    ),
                    modifier = Modifier.testTag("date_filter_yesterday")
                )

                FilterChip(
                    selected = dateFilter == HistoryDateFilter.THIS_WEEK,
                    onClick = { onDateFilterChanged(HistoryDateFilter.THIS_WEEK, null) },
                    label = { Text("This Week") },
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
                        selected = dateFilter == HistoryDateFilter.THIS_WEEK
                    ),
                    modifier = Modifier.testTag("date_filter_week")
                )

                FilterChip(
                    selected = dateFilter == HistoryDateFilter.THIS_MONTH,
                    onClick = { onDateFilterChanged(HistoryDateFilter.THIS_MONTH, null) },
                    label = { Text("This Month") },
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
                        selected = dateFilter == HistoryDateFilter.THIS_MONTH
                    ),
                    modifier = Modifier.testTag("date_filter_month")
                )

                if (dateFilter == HistoryDateFilter.CUSTOM && customDateEpoch != null) {
                    FilterChip(
                        selected = true,
                        onClick = { onDateFilterChanged(HistoryDateFilter.ALL, null) },
                        label = { Text(DateFilterHelper.formatCustomDateHeader(customDateEpoch)) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear custom date",
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MayraCyan.copy(alpha = 0.25f),
                            selectedLabelColor = MayraCyan
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = MayraCyan,
                            selectedBorderColor = MayraCyan,
                            enabled = true,
                            selected = true
                        ),
                        modifier = Modifier.testTag("date_filter_custom_active")
                    )
                } else {
                    FilterChip(
                        selected = false,
                        onClick = openCalendarPicker,
                        label = { Text("Pick Date...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MayraDarkSurfaceElevated,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = MayraDarkSurfaceBorder,
                            selectedBorderColor = MayraCyan,
                            enabled = true,
                            selected = false
                        ),
                        modifier = Modifier.testTag("date_filter_pick_chip")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Active Chats / Archived Filter Chips & Active Filter Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showArchived,
                        onClick = { onToggleArchivedFilter(false) },
                        label = { Text("Active") },
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

                if (isFilterActive) {
                    TextButton(
                        onClick = onClearFilters,
                        modifier = Modifier.testTag("clear_history_filters_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FilterListOff,
                            contentDescription = null,
                            tint = MayraCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Clear filters",
                            style = MaterialTheme.typography.labelMedium,
                            color = MayraCyan
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 6. Conversations List or Empty State
            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp)
                        .testTag("no_conversations_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isFilterActive) Icons.Outlined.Search else Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = when {
                                searchQuery.isNotBlank() && dateFilter != HistoryDateFilter.ALL ->
                                    "No conversations matching '$searchQuery' for the selected date"
                                searchQuery.isNotBlank() ->
                                    "No conversations found matching '$searchQuery'"
                                dateFilter != HistoryDateFilter.ALL ->
                                    "No conversations found for the selected date"
                                showArchived ->
                                    "No archived conversations"
                                else ->
                                    "No saved conversations yet"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isFilterActive) {
                            TextButton(
                                onClick = onClearFilters,
                                modifier = Modifier.testTag("empty_state_reset_filters_button")
                            ) {
                                Text(
                                    text = "Reset search & filters",
                                    color = MayraCyan,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
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
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (conv.isPinned) {
                                            Icon(
                                                imageVector = Icons.Filled.PushPin,
                                                contentDescription = "Pinned",
                                                tint = MayraCyan,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = conv.title,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (isActive) MayraCyan else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Action buttons: Pin, Rename, Archive, Delete
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { onPinConversation(conv.id) },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .testTag("pin_conversation_${conv.id}")
                                        ) {
                                            Icon(
                                                imageVector = if (conv.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                                contentDescription = if (conv.isPinned) "Unpin" else "Pin",
                                                tint = if (conv.isPinned) MayraCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                renamingConversation = conv
                                                renameText = conv.title
                                            },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .testTag("rename_conversation_${conv.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Edit,
                                                contentDescription = "Rename",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { onArchiveConversation(conv.id) },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .testTag("archive_conversation_${conv.id}")
                                        ) {
                                            Icon(
                                                imageVector = if (conv.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                                contentDescription = if (conv.isArchived) "Unarchive" else "Archive",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { deletingConversation = conv },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .testTag("delete_conversation_${conv.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = "Delete",
                                                tint = MayraRose.copy(alpha = 0.8f),
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Date badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccessTime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = DateFilterHelper.formatConversationDate(conv.updatedAt),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (renamingConversation != null) {
        val convToRename = renamingConversation!!
        AlertDialog(
            onDismissRequest = { renamingConversation = null },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MayraCyan,
                        focusedLabelColor = MayraCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_title_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isNotBlank()) {
                            onRenameConversation(convToRename.id, trimmed)
                        }
                        renamingConversation = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MayraCyan),
                    modifier = Modifier.testTag("confirm_rename_button")
                ) {
                    Text("Save", color = Color(0xFF003548))
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
            modifier = Modifier.testTag("rename_conversation_dialog")
        )
    }

    // Delete Confirmation Dialog
    if (deletingConversation != null) {
        val convToDelete = deletingConversation!!
        AlertDialog(
            onDismissRequest = { deletingConversation = null },
            title = { Text("Delete Conversation") },
            text = {
                Text("Are you sure you want to delete \"${convToDelete.title}\"? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteConversation(convToDelete.id)
                        deletingConversation = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MayraRose),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingConversation = null },
                    modifier = Modifier.testTag("cancel_delete_button")
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MayraDarkSurfaceElevated,
            modifier = Modifier.testTag("delete_conversation_dialog")
        )
    }
}
