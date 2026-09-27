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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraCyanBright
import com.example.ui.theme.MayraDarkSurface
import com.example.ui.theme.MayraDarkSurfaceBorder
import com.example.ui.theme.MayraDarkSurfaceElevated
import com.example.ui.theme.MayraIndigo
import com.example.ui.theme.MayraViolet

private data class SuggestionCardData(
    val title: String,
    val prompt: String,
    val icon: ImageVector,
    val badge: String
)

@Composable
fun EmptyChatView(
    onSuggestionClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = listOf(
        SuggestionCardData(
            title = "Bengali (বাংলা)",
            prompt = "কোয়ান্টাম কম্পিউটিং কী? সহজ বাংলায় বুঝিয়ে বলো।",
            icon = Icons.Outlined.Translate,
            badge = "বাংলা"
        ),
        SuggestionCardData(
            title = "Kotlin Architecture",
            prompt = "Write a modern Kotlin Coroutines Flow pattern for Android Jetpack Compose.",
            icon = Icons.Outlined.Code,
            badge = "Code"
        ),
        SuggestionCardData(
            title = "Hindi (हिन्दी)",
            prompt = "आर्टिफिशियल इंटेलिजेंस का भविष्य क्या है? संक्षेप में बताएं।",
            icon = Icons.Outlined.Psychology,
            badge = "हिन्दी"
        ),
        SuggestionCardData(
            title = "Brainstorm Concepts",
            prompt = "Brainstorm 3 innovative AI startup concepts focused on privacy and edge devices.",
            icon = Icons.Outlined.Lightbulb,
            badge = "Ideas"
        ),
        SuggestionCardData(
            title = "Arabic & Spanish",
            prompt = "¡Hola Mayra! ¿Cómo puedes ayudarme hoy?",
            icon = Icons.Outlined.AutoAwesome,
            badge = "Multilingual"
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Futuristic Monogram Hero
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            MayraDarkSurface
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(listOf(MayraCyanBright, MayraIndigo, MayraViolet)),
                    shape = RoundedCornerShape(22.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "M",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black
                ),
                color = MayraCyanBright
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Greeting
        Text(
            text = stringResource(R.string.empty_greeting),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle
        Text(
            text = stringResource(R.string.empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Architecture capabilities pill
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MayraDarkSurfaceElevated)
                .border(0.5.dp, MayraDarkSurfaceBorder, CircleShape)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MayraCyan,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "Multilingual • High Precision • Scalable Core",
                style = MaterialTheme.typography.labelSmall,
                color = MayraCyan
            )
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Suggestion prompt cards
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            suggestions.forEachIndexed { index, suggestion ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MayraDarkSurfaceElevated.copy(alpha = 0.8f))
                        .border(1.dp, MayraDarkSurfaceBorder, RoundedCornerShape(14.dp))
                        .clickable { onSuggestionClicked(suggestion.prompt) }
                        .padding(14.dp)
                        .testTag("suggestion_chip_$index")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MayraDarkSurface)
                                    .border(0.5.dp, MayraDarkSurfaceBorder, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = suggestion.icon,
                                    contentDescription = null,
                                    tint = MayraCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = suggestion.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = suggestion.prompt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MayraCyan.copy(alpha = 0.12f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = suggestion.badge,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MayraCyan
                            )
                        }
                    }
                }
            }
        }
    }
}
