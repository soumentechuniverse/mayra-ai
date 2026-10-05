package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.Attachment
import com.example.domain.model.AttachmentType
import com.example.domain.model.VoiceState
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraCyanBright
import com.example.ui.theme.MayraDarkSurfaceBorder
import com.example.ui.theme.MayraDarkSurfaceElevated
import com.example.ui.theme.MayraDarkSurfaceHover
import com.example.ui.theme.MayraIndigo
import com.example.ui.theme.MayraRose
import com.example.ui.theme.MayraViolet

@Composable
fun MessageComposer(
    text: String,
    isGenerating: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onAttachmentClicked: () -> Unit,
    onVoiceClicked: () -> Unit,
    onStopGenerating: () -> Unit = {},
    pendingAttachments: List<Attachment> = emptyList(),
    onRemoveAttachment: (String) -> Unit = {},
    isProcessingAttachments: Boolean = false,
    voiceState: VoiceState = VoiceState.Idle,
    onCancelVoice: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hasText = text.isNotBlank()
    val canSend = (hasText || pendingAttachments.isNotEmpty()) && !isGenerating && !isProcessingAttachments

    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    val imeInsets = androidx.compose.foundation.layout.WindowInsets.ime
    val isImeOpen = imeInsets.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    val navBarPadding = if (!isImeOpen) {
        androidx.compose.foundation.layout.WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
    } else 0.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .padding(bottom = navBarPadding)
    ) {
        // Active Listening Banner (Speech-To-Text in progress)
        if (voiceState is VoiceState.Listening) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF191E2E))
                    .border(1.dp, MayraRose.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("listening_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(MayraRose)
                    )
                    Text(
                        text = if (voiceState.partialText.isNotBlank()) voiceState.partialText else "Listening… Speak now",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (voiceState.partialText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onVoiceClicked,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("stop_listening_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Finish speaking",
                            tint = MayraCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onCancelVoice,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("cancel_listening_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cancel voice input",
                            tint = MayraRose,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Pending Attachments Preview Tray
        if (pendingAttachments.isNotEmpty() || isProcessingAttachments) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .testTag("pending_attachments_tray"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(pendingAttachments, key = { it.id }) { att ->
                    PendingAttachmentChip(
                        attachment = att,
                        onRemove = { onRemoveAttachment(att.id) }
                    )
                }

                if (isProcessingAttachments) {
                    item {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MayraDarkSurfaceElevated)
                                .border(1.dp, MayraCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("attachment_processing_indicator"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MayraCyan
                            )
                            Text(
                                text = "Reading file…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MayraCyan
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MayraDarkSurfaceElevated)
                .border(
                    width = 1.dp,
                    brush = if (canSend) {
                        Brush.horizontalGradient(listOf(MayraCyan, MayraIndigo))
                    } else {
                        SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    },
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Attachment Button
            IconButton(
                onClick = onAttachmentClicked,
                modifier = Modifier
                    .size(40.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("attachment_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.AttachFile,
                    contentDescription = stringResource(R.string.attach_file),
                    tint = if (pendingAttachments.isNotEmpty()) MayraCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Text Input Box (multiline, auto-expanding up to 130dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = if (pendingAttachments.isNotEmpty()) "Add instructions or tap send…" else stringResource(R.string.message_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                BasicTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 24.dp, max = 130.dp)
                        .testTag("message_input"),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textDirection = TextDirection.ContentOrLtr
                    ),
                    cursorBrush = SolidColor(MayraCyan),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = if (canSend) ImeAction.Send else ImeAction.Default
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend) {
                                onSend()
                            }
                        }
                    )
                )
            }

            // Voice Interaction Button (Speech Recognition)
            val isListening = voiceState is VoiceState.Listening
            IconButton(
                onClick = onVoiceClicked,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isListening) MayraRose.copy(alpha = 0.2f) else Color.Transparent
                    )
                    .minimumInteractiveComponentSize()
                    .testTag("voice_button")
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                    contentDescription = if (isListening) "Stop listening" else stringResource(R.string.voice_input),
                    tint = if (isListening) MayraRose else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .scale(if (isListening) pulseScale else 1f)
                )
            }

            // Send Button or Generating / Stop Button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) {
                            Brush.linearGradient(listOf(MayraCyanBright, MayraIndigo))
                        } else if (isGenerating) {
                            SolidColor(MayraDarkSurfaceHover)
                        } else {
                            SolidColor(Color.Transparent)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isGenerating) {
                    IconButton(
                        onClick = onStopGenerating,
                        modifier = Modifier
                            .size(42.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("stop_generating_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MayraCyan
                            )
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MayraCyan)
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = onSend,
                        enabled = canSend,
                        modifier = Modifier
                            .size(42.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = stringResource(R.string.send_message),
                            tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingAttachmentChip(
    attachment: Attachment,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141A29))
            .border(1.dp, MayraDarkSurfaceBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (attachment.type) {
                AttachmentType.IMAGE -> {
                    if (!attachment.localUri.isNullOrBlank()) {
                        AsyncImage(
                            model = attachment.localUri,
                            contentDescription = attachment.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MayraCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("IMG", style = MaterialTheme.typography.labelSmall, color = MayraCyan)
                        }
                    }
                }
                AttachmentType.PDF -> {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MayraRose.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PictureAsPdf,
                            contentDescription = null,
                            tint = MayraRose,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                AttachmentType.DOCUMENT -> {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MayraIndigo.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = MayraCyanBright,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.widthIn(max = 140.dp)) {
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (attachment.formattedSize.isNotEmpty()) {
                    Text(
                        text = attachment.formattedSize,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF232D42))
                    .testTag("remove_attachment_${attachment.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove attachment",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
