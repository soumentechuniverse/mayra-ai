package com.example.ui.components

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraDarkBorder
import com.example.ui.theme.MayraDarkSurface
import com.example.ui.theme.MayraDarkSurfaceVariant
import com.example.ui.theme.MayraIndigo
import com.example.ui.theme.MayraPurple
import com.example.ui.theme.MayraTextMuted
import com.example.ui.theme.MayraTextPrimary
import com.example.ui.theme.MayraTextSecondary

data class PromptSuggestion(
    val title: String,
    val prompt: String,
    val icon: ImageVector
)

@Composable
fun WelcomeScreen(
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = listOf(
        PromptSuggestion(
            title = "Fast Q&A",
            prompt = "Hello! What can you do?",
            icon = Icons.Default.Psychology
        ),
        PromptSuggestion(
            title = "Coding",
            prompt = "Write a Kotlin Flow example with error handling and retry",
            icon = Icons.Default.Code
        ),
        PromptSuggestion(
            title = "Multilingual",
            prompt = "একটি সুন্দর বাংলা কবিতা অথবা গল্প লিখুন",
            icon = Icons.Default.Language
        ),
        PromptSuggestion(
            title = "Image Creation",
            prompt = "Generate an image of a futuristic cyber city at sunset",
            icon = Icons.Default.Image
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("welcome_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Mayra Logo / Glowing Badge
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MayraIndigo)
                .border(2.dp, MayraCyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Mayra AI",
                tint = MayraCyan,
                modifier = Modifier.size(38.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome to Mayra AI",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MayraTextPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Fast, production-quality multimodal assistant",
            fontSize = 14.sp,
            color = MayraCyan,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Created by Soumen Mondal",
            fontSize = 12.sp,
            color = MayraTextMuted
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Suggested Prompt Cards
        Text(
            text = "Try asking:",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MayraTextSecondary,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            suggestions.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MayraDarkSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MayraDarkBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPrompt(item.prompt) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MayraDarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = MayraCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MayraTextPrimary
                            )
                            Text(
                                text = item.prompt,
                                fontSize = 12.sp,
                                color = MayraTextMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
