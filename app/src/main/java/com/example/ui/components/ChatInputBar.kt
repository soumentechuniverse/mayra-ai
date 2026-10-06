package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.speech.SpeechRecognizerHelper
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraDarkBorder
import com.example.ui.theme.MayraDarkSurface
import com.example.ui.theme.MayraDarkSurfaceVariant
import com.example.ui.theme.MayraErrorRed
import com.example.ui.theme.MayraIndigo
import com.example.ui.theme.MayraTextMuted
import com.example.ui.theme.MayraTextPrimary
import com.example.ui.theme.MayraTextSecondary

data class AttachmentInfo(
    val uri: Uri,
    val mimeType: String,
    val name: String,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttachmentInfo) return false
        return uri == other.uri
    }

    override fun hashCode(): Int = uri.hashCode()
}

@Composable
fun ChatInputBar(
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    isGenerating: Boolean,
    attachedFile: AttachmentInfo?,
    onAttachmentSelected: (AttachmentInfo?) -> Unit,
    speechRecognizerHelper: SpeechRecognizerHelper,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isListening by speechRecognizerHelper.isListening.collectAsState()

    // Photo Picker (Android standard photo picker - zero storage permission needed)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                if (bytes != null) {
                    onAttachmentSelected(
                        AttachmentInfo(
                            uri = uri,
                            mimeType = mime,
                            name = "image_${System.currentTimeMillis()}.jpg",
                            bytes = bytes
                        )
                    )
                }
            } catch (_: Exception) {}
        }
    }

    // Document Picker
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                val mime = context.contentResolver.getType(uri) ?: "text/plain"
                val name = uri.lastPathSegment ?: "document"
                if (bytes != null) {
                    onAttachmentSelected(
                        AttachmentInfo(
                            uri = uri,
                            mimeType = mime,
                            name = name,
                            bytes = bytes
                        )
                    )
                }
            } catch (_: Exception) {}
        }
    }

    // Microphone Permission Launcher
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            speechRecognizerHelper.startListening { spoken ->
                val newText = if (inputText.isBlank()) spoken else "$inputText $spoken"
                onInputTextChanged(newText)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MayraDarkSurface)
            .border(
                androidx.compose.foundation.BorderStroke(1.dp, MayraDarkBorder),
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Attachment preview chip if selected
        if (attachedFile != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MayraDarkSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MayraCyan),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (attachedFile.mimeType.startsWith("image/")) Icons.Default.Image else Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = MayraCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = attachedFile.name,
                        color = MayraTextPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { onAttachmentSelected(null) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove attachment",
                            tint = MayraTextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Input row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attachment button
            IconButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("attach_file_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Attach image",
                    tint = MayraCyan,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = {
                    docPickerLauncher.launch(
                        arrayOf(
                            "application/pdf",
                            "text/plain",
                            "text/markdown",
                            "text/csv",
                            "application/json"
                        )
                    )
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attach document",
                    tint = MayraTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Text input container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MayraDarkSurfaceVariant)
                    .border(1.dp, MayraDarkBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = if (isListening) "Listening..." else "Message Mayra...",
                        color = if (isListening) MayraCyan else MayraTextMuted,
                        fontSize = 15.sp
                    )
                }

                BasicTextField(
                    value = inputText,
                    onValueChange = onInputTextChanged,
                    textStyle = TextStyle(
                        color = MayraTextPrimary,
                        fontSize = 15.sp
                    ),
                    cursorBrush = SolidColor(MayraCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_text_field")
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Speech-to-Text Microphone button
            val micColor by animateColorAsState(
                targetValue = if (isListening) MayraErrorRed else MayraCyan,
                label = "micColor"
            )

            IconButton(
                onClick = {
                    if (isListening) {
                        speechRecognizerHelper.stopListening()
                    } else {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            speechRecognizerHelper.startListening { spoken ->
                                val newText = if (inputText.isBlank()) spoken else "$inputText $spoken"
                                onInputTextChanged(newText)
                            }
                        } else {
                            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("voice_input_button")
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isListening) "Stop listening" else "Dictate speech",
                    tint = micColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Send / Stop button
            val canSend = (inputText.isNotBlank() || attachedFile != null) && !isGenerating

            IconButton(
                onClick = {
                    if (isGenerating) {
                        onStopGeneration()
                    } else if (canSend) {
                        onSendMessage()
                    }
                },
                enabled = isGenerating || canSend,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isGenerating) MayraErrorRed else if (canSend) MayraIndigo else MayraDarkSurfaceVariant)
                    .testTag("send_message_button")
            ) {
                if (isGenerating) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop generating",
                        tint = MayraTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = if (canSend) MayraCyan else MayraTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
