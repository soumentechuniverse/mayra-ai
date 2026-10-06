package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entity.MessageEntity
import com.example.data.remote.RetrofitClient
import com.example.data.remote.model.WebSourceCitation
import com.example.ui.theme.MayraAssistantBubble
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraDarkBorder
import com.example.ui.theme.MayraDarkSurface
import com.example.ui.theme.MayraDarkSurfaceVariant
import com.example.ui.theme.MayraErrorRed
import com.example.ui.theme.MayraIndigo
import com.example.ui.theme.MayraTextMuted
import com.example.ui.theme.MayraTextPrimary
import com.example.ui.theme.MayraTextSecondary
import com.example.ui.theme.MayraUserBubble

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatMessageItem(
    message: MessageEntity,
    onRetry: (String) -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Assistant Avatar
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MayraIndigo)
                    .border(1.dp, MayraCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Mayra AI",
                    tint = MayraCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Message Content Bubble
        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // User Attachment Display
            if (isUser && (!message.attachmentUri.isNullOrBlank() || !message.attachmentName.isNullOrBlank())) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MayraDarkSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MayraDarkBorder),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attachment",
                            tint = MayraCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = message.attachmentName ?: "Attachment",
                            color = MayraTextPrimary,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // Bubble container
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) MayraUserBubble else MayraAssistantBubble,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (message.status == "ERROR") MayraErrorRed else MayraDarkBorder
                ),
                modifier = Modifier.testTag(if (isUser) "user_message_bubble" else "assistant_message_bubble")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Generated Image (if any)
                    if (!message.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = message.imageUrl,
                            contentDescription = "Generated image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, MayraDarkBorder, RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Content rendering
                    if (message.status == "SENDING" && message.content.isBlank()) {
                        ThinkingIndicator()
                    } else {
                        MarkdownContent(
                            content = message.content,
                            textColor = MayraTextPrimary
                        )

                        // Subtle typing indicator if still streaming
                        if (message.status == "SENDING") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MayraCyan
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Streaming...",
                                    fontSize = 11.sp,
                                    color = MayraTextMuted
                                )
                            }
                        }
                    }

                    // Web Search Sources Citations
                    if (!message.sourcesJson.isNullOrBlank()) {
                        val citations = remember(message.sourcesJson) {
                            try {
                                RetrofitClient.json.decodeFromString<List<WebSourceCitation>>(message.sourcesJson)
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }

                        if (citations.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Sources & Citations:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MayraCyan
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                citations.forEach { citation ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MayraDarkSurfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MayraCyan.copy(alpha = 0.5f)),
                                        modifier = Modifier.clickable {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(citation.url)))
                                            } catch (_: Exception) {}
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Language,
                                                contentDescription = null,
                                                tint = MayraCyan,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = citation.title.take(24),
                                                fontSize = 11.sp,
                                                color = MayraTextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Error retry affordance
                    if (message.status == "ERROR") {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { onRetry(message.id) },
                            modifier = Modifier.testTag("retry_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MayraCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MayraCyan),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Message Actions (Only for Assistant messages when done)
            if (!isUser && message.status == "SENT" && message.content.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onSpeak(message.content) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Read aloud",
                            tint = MayraTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("response", message.content))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy text",
                            tint = MayraTextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // User Avatar
        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MayraDarkSurfaceVariant)
                    .border(1.dp, MayraIndigo, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User",
                    tint = MayraTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
