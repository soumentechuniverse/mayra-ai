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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.local.AppUpdatePreferences
import com.example.data.service.AppUpdateInfo
import com.example.domain.model.AiModelConfig
import com.example.domain.model.MemoryCategory
import com.example.domain.model.MemoryItem
import com.example.domain.model.SearchMode
import com.example.domain.model.VoiceLanguage
import com.example.domain.model.VoiceSettings
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraDarkSurface
import com.example.ui.theme.MayraDarkSurfaceBorder
import com.example.ui.theme.MayraDarkSurfaceElevated
import com.example.ui.theme.MayraRose

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    isOpen: Boolean,
    selectedModel: AiModelConfig,
    availableModels: List<AiModelConfig>,
    isDarkTheme: Boolean,
    onModelSelected: (AiModelConfig) -> Unit,
    onTemperatureChanged: (Float) -> Unit,
    onToggleTheme: () -> Unit,
    onClearChat: () -> Unit,
    onDismiss: () -> Unit,
    searchMode: SearchMode = SearchMode.AUTO,
    onSearchModeChanged: (SearchMode) -> Unit = {},
    voiceSettings: VoiceSettings = VoiceSettings(),
    onVoiceInputLanguageChanged: (VoiceLanguage) -> Unit = {},
    onVoiceAutoSpeakToggled: (Boolean) -> Unit = {},
    onVoiceOutputLanguageChanged: (VoiceLanguage) -> Unit = {},
    // Memory Controls
    isMemoryEnabled: Boolean = true,
    onToggleMemoryEnabled: (Boolean) -> Unit = {},
    memories: List<MemoryItem> = emptyList(),
    isManageMemoryOpen: Boolean = false,
    onOpenManageMemory: () -> Unit = {},
    onCloseManageMemory: () -> Unit = {},
    onAddMemory: (String, MemoryCategory) -> Unit = { _, _ -> },
    onToggleMemoryItem: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteMemory: (String) -> Unit = {},
    onClearAllMemories: () -> Unit = {},
    // App Update
    isCheckingUpdate: Boolean = false,
    updateInfo: AppUpdateInfo? = null,
    updateStatusMessage: String? = null,
    updateSourceUrl: String = AppUpdatePreferences.DEFAULT_UPDATE_URL,
    isUpdateSourceConfigOpen: Boolean = false,
    onCheckForUpdate: () -> Unit = {},
    onUpdateSourceUrlChanged: (String) -> Unit = {},
    onResetUpdateSourceUrl: () -> Unit = {},
    onToggleUpdateSourceConfig: () -> Unit = {},
    onDownloadAppUpdate: (android.content.Context, String) -> Unit = { _, _ -> },
    onDismissUpdateDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local dialog state for Clear All Memory confirmation
    var showClearMemoriesDialog by remember { mutableStateOf(false) }
    var newMemoryText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(MemoryCategory.PREFERENCE) }
    var showAddMemoryInput by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MayraDarkSurface,
        modifier = modifier.testTag("settings_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = MayraCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Mayra AI Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Model Selection Section
            Text(
                text = "INTELLIGENCE ENGINE",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MayraCyan
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                availableModels.forEach { model ->
                    val isSelected = model.modelId == selectedModel.modelId
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MayraDarkSurfaceElevated else MayraDarkSurface
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = if (isSelected) MayraCyan else MayraDarkSurfaceBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onModelSelected(model) }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = model.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MayraCyan),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = "Selected",
                                        tint = MayraDarkSurface,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Creativity / Temperature
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Creativity & Precision (Temperature)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = String.format("%.2f", selectedModel.temperature),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MayraCyan
                )
            }

            Slider(
                value = selectedModel.temperature,
                onValueChange = onTemperatureChanged,
                valueRange = 0.0f..1.0f,
                steps = 9,
                colors = SliderDefaults.colors(
                    thumbColor = MayraCyan,
                    activeTrackColor = MayraCyan,
                    inactiveTrackColor = MayraDarkSurfaceBorder
                ),
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Multilingual Support Status Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MayraDarkSurfaceElevated)
                    .border(1.dp, MayraDarkSurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = null,
                        tint = MayraCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Unicode & Multilingual Core",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Native support for Bengali, English, Hindi, Urdu, Arabic, Spanish, French, German, Chinese, Japanese, and more. Mixed language dialogue is fully enabled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // STEP 6: PERSONAL MEMORY SECTION
            Text(
                text = "PERSONAL MEMORY",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MayraCyan
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Memory ON/OFF Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MayraDarkSurfaceElevated)
                    .border(1.dp, MayraDarkSurfaceBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mayra AI Memory",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Remember explicit user instructions & preferences across chats",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isMemoryEnabled,
                    onCheckedChange = onToggleMemoryEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MayraCyan
                    ),
                    modifier = Modifier.testTag("memory_toggle_switch")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Manage Memory Button
            FilledTonalButton(
                onClick = onOpenManageMemory,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MayraCyan.copy(alpha = 0.15f),
                    contentColor = MayraCyan
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manage_memory_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Manage Memory (${memories.size} saved)",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Web Search Grounding Mode Section
            Text(
                text = "Web Search & Grounding",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Controls when Mayra AI accesses Google Search for live, real-time facts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(SearchMode.AUTO, SearchMode.ENABLED, SearchMode.DISABLED).forEach { mode ->
                    val isSelected = searchMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) MayraCyan.copy(alpha = 0.2f) else MayraDarkSurfaceElevated)
                            .border(
                                1.dp,
                                if (isSelected) MayraCyan else MayraDarkSurfaceBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onSearchModeChanged(mode) }
                            .padding(vertical = 10.dp)
                            .testTag("search_mode_${mode.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (mode) {
                                SearchMode.AUTO -> "Auto"
                                SearchMode.ENABLED -> "Always"
                                SearchMode.DISABLED -> "Off"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) MayraCyan else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Voice Interaction Settings
            Text(
                text = "Voice Interaction",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Configure speech recognition and text-to-speech audio responses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Voice Input Language Selector
            Text(
                text = "Voice Input Language",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(VoiceLanguage.entries) { lang ->
                    val isSelected = voiceSettings.inputLanguage == lang
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MayraCyan.copy(alpha = 0.2f) else MayraDarkSurfaceElevated)
                            .border(1.dp, if (isSelected) MayraCyan else MayraDarkSurfaceBorder, RoundedCornerShape(8.dp))
                            .clickable { onVoiceInputLanguageChanged(lang) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("voice_input_lang_${lang.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${lang.displayName} (${lang.nativeName})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) MayraCyan else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Auto-speak response switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MayraDarkSurfaceElevated)
                    .border(1.dp, MayraDarkSurfaceBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Speak AI Responses",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Automatically read out completed responses",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = voiceSettings.autoSpeakOutput,
                    onCheckedChange = onVoiceAutoSpeakToggled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MayraCyan
                    ),
                    modifier = Modifier.testTag("auto_speak_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Voice Output Language Selector
            Text(
                text = "Voice Output Language",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(VoiceLanguage.entries) { lang ->
                    val isSelected = voiceSettings.outputLanguage == lang
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MayraCyan.copy(alpha = 0.2f) else MayraDarkSurfaceElevated)
                            .border(1.dp, if (isSelected) MayraCyan else MayraDarkSurfaceBorder, RoundedCornerShape(8.dp))
                            .clickable { onVoiceOutputLanguageChanged(lang) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("voice_output_lang_${lang.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${lang.displayName} (${lang.nativeName})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) MayraCyan else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // App Update Section
            Text(
                text = "APP UPDATE",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MayraCyan
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Check the official release channel for newer versions and changelogs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MayraDarkSurfaceElevated)
                    .border(1.dp, MayraDarkSurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
                    .testTag("app_update_section")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MayraCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.SystemUpdate,
                                    contentDescription = null,
                                    tint = MayraCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Mayra AI",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Current: v${BuildConfig.VERSION_NAME.ifBlank { "1.0" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("current_version_text")
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = onCheckForUpdate,
                            enabled = !isCheckingUpdate,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MayraCyan.copy(alpha = 0.2f),
                                contentColor = MayraCyan
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("check_for_updates_button")
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MayraCyan
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Checking...", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Check Now", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Status message if any
                    updateStatusMessage?.let { status ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (updateInfo?.isUpdateAvailable == true) MayraCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("update_status_message")
                            )
                        }
                    }

                    // Update details card if update available
                    if (updateInfo != null && updateInfo.isUpdateAvailable) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0B192C))
                                .border(1.dp, MayraCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                                .testTag("update_available_card")
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Update Available: v${updateInfo.latestVersion}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MayraCyan,
                                        modifier = Modifier.testTag("latest_version_text")
                                    )
                                    updateInfo.releaseDate?.let { date ->
                                        Text(
                                            text = date.take(10),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "What's New:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = updateInfo.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("update_release_notes")
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                val context = LocalContext.current
                                Button(
                                    onClick = { onDownloadAppUpdate(context, updateInfo.downloadUrl) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MayraCyan,
                                        contentColor = MayraDarkSurface
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("download_update_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Download & Install Update",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Configurable update source toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleUpdateSourceConfig() }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Release Source Configuration",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isUpdateSourceConfigOpen) "Hide ▲" else "Configure ▼",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MayraCyan,
                            modifier = Modifier.testTag("configure_update_source_button")
                        )
                    }

                    if (isUpdateSourceConfigOpen) {
                        Spacer(modifier = Modifier.height(6.dp))
                        var tempUrl by remember(updateSourceUrl) { mutableStateOf(updateSourceUrl) }

                        OutlinedTextField(
                            value = tempUrl,
                            onValueChange = {
                                tempUrl = it
                                onUpdateSourceUrlChanged(it)
                            },
                            label = { Text("Update Source URL (GitHub or Custom JSON)") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MayraCyan,
                                unfocusedBorderColor = MayraDarkSurfaceBorder,
                                focusedLabelColor = MayraCyan
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_source_input")
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    onResetUpdateSourceUrl()
                                    tempUrl = AppUpdatePreferences.DEFAULT_UPDATE_URL
                                },
                                modifier = Modifier.testTag("reset_update_source_button")
                            ) {
                                Text("Reset to Official Release", color = MayraCyan, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Clear Conversation Danger Row
            FilledTonalButton(
                onClick = {
                    onClearChat()
                    onDismiss()
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MayraRose.copy(alpha = 0.15f),
                    contentColor = MayraRose
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clear_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Clear Current Messages", style = MaterialTheme.typography.labelMedium)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // App Version & Architecture Footnote
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Mayra AI v1.0.0 (Step 6 Conversation & Memory)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // MANAGE MEMORY MODAL DIALOG
    if (isManageMemoryOpen) {
        AlertDialog(
            onDismissRequest = onCloseManageMemory,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Personal Memory",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onCloseManageMemory) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close manage memory",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Explicit preferences and instructions remembered by Mayra AI across conversations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Add Memory Button / Form Toggle
                    if (!showAddMemoryInput) {
                        FilledTonalButton(
                            onClick = { showAddMemoryInput = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MayraCyan.copy(alpha = 0.15f),
                                contentColor = MayraCyan
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("show_add_memory_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Custom Memory", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        // Quick Add Memory Input
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MayraDarkSurface)
                                .border(1.dp, MayraDarkSurfaceBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "Category",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(MemoryCategory.entries) { cat ->
                                    FilterChip(
                                        selected = selectedCategory == cat,
                                        onClick = { selectedCategory = cat },
                                        label = { Text(cat.displayName) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MayraCyan.copy(alpha = 0.2f),
                                            selectedLabelColor = MayraCyan
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = newMemoryText,
                                onValueChange = { newMemoryText = it },
                                placeholder = { Text("e.g. User prefers Bengali responses") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MayraCyan,
                                    cursorColor = MayraCyan
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("new_memory_text_field")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    showAddMemoryInput = false
                                    newMemoryText = ""
                                }) {
                                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        val trimmed = newMemoryText.trim()
                                        if (trimmed.isNotEmpty()) {
                                            onAddMemory(trimmed, selectedCategory)
                                            newMemoryText = ""
                                            showAddMemoryInput = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MayraCyan,
                                        contentColor = Color(0xFF003548)
                                    ),
                                    modifier = Modifier.testTag("save_new_memory_button")
                                ) {
                                    Text("Save")
                                }
                            }
                        }
                    }

                    if (memories.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No personal memories stored yet.\nAsk Mayra AI to \"Remember that...\" in chat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            memories.forEach { mem ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MayraDarkSurface)
                                        .border(1.dp, MayraDarkSurfaceBorder, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                        .testTag("memory_item_${mem.id}"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MayraCyan.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = mem.category.displayName,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                ),
                                                color = MayraCyan
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = mem.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (mem.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Switch(
                                            checked = mem.enabled,
                                            onCheckedChange = { onToggleMemoryItem(mem.id, it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = MayraCyan
                                            ),
                                            modifier = Modifier.testTag("toggle_memory_${mem.id}")
                                        )

                                        IconButton(
                                            onClick = { onDeleteMemory(mem.id) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("delete_memory_${mem.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = "Delete memory item",
                                                tint = MayraRose.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Clear all memories button
                        FilledTonalButton(
                            onClick = { showClearMemoriesDialog = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MayraRose.copy(alpha = 0.15f),
                                contentColor = MayraRose
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("clear_all_memory_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear All Memories", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onCloseManageMemory,
                    modifier = Modifier.testTag("done_manage_memory_button")
                ) {
                    Text("Done", color = MayraCyan)
                }
            },
            containerColor = MayraDarkSurfaceElevated,
            modifier = Modifier.testTag("manage_memory_dialog")
        )
    }

    // Confirmation dialog for clearing all memories
    if (showClearMemoriesDialog) {
        AlertDialog(
            onDismissRequest = { showClearMemoriesDialog = false },
            title = {
                Text(
                    text = "Clear All Memories?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to clear all remembered preferences and instructions? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllMemories()
                        showClearMemoriesDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MayraRose,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_clear_all_memory_button")
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearMemoriesDialog = false },
                    modifier = Modifier.testTag("cancel_clear_all_memory_button")
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MayraDarkSurfaceElevated,
            modifier = Modifier.testTag("clear_all_memories_dialog")
        )
    }
}
