package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraCyanBright
import com.example.ui.theme.MayraDarkBg
import com.example.ui.theme.MayraDarkSurface
import com.example.ui.theme.MayraDarkSurfaceBorder
import com.example.ui.theme.MayraDarkSurfaceElevated
import com.example.ui.theme.MayraIndigo
import com.example.ui.theme.MayraViolet

private data class WelcomeStarterOption(
    val title: String,
    val subtitle: String,
    val prompt: String,
    val icon: ImageVector,
    val badge: String,
    val accentColor: Color
)

@Composable
fun EmptyChatView(
    onSuggestionClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Subtle breathing animation for Mayra welcome icon
    val infiniteTransition = rememberInfiniteTransition(label = "WelcomeIconTransition")
    val floatY by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    // The user explicitly requested these starter options:
    // - বাংলায় শুরু করুন
    // - Start in English
    // - हिंदी में शुरू करें
    // - Start a conversation
    // - Ask anything
    val starterOptions = listOf(
        WelcomeStarterOption(
            title = "বাংলায় শুরু করুন",
            subtitle = "নমস্কার! সহজ বাংলায় যেকোনো বিষয়ে আলোচনা করুন",
            prompt = "নমস্কার Mayra! আমি বাংলায় কথা বলতে চাই।",
            icon = Icons.Outlined.Translate,
            badge = "বাংলা",
            accentColor = MayraCyan
        ),
        WelcomeStarterOption(
            title = "Start in English",
            subtitle = "Explore ideas, generate code, or ask in-depth questions",
            prompt = "Hello Mayra! Let's get started in English.",
            icon = Icons.Outlined.Language,
            badge = "English",
            accentColor = MayraCyanBright
        ),
        WelcomeStarterOption(
            title = "हिंदी में शुरू करें",
            subtitle = "नमस्ते! किसी भी विषय पर हिंदी में बातचीत शुरू करें",
            prompt = "नमस्ते Mayra! चलिए हिंदी में बातचीत शुरू करते हैं।",
            icon = Icons.Outlined.Psychology,
            badge = "हिन्दी",
            accentColor = MayraIndigo
        ),
        WelcomeStarterOption(
            title = "Start a conversation",
            subtitle = "Engage in intelligent dialogue, brainstorming, or creative writing",
            prompt = "Hello Mayra, let's start a conversation.",
            icon = Icons.AutoMirrored.Outlined.Chat,
            badge = "Dialogue",
            accentColor = MayraViolet
        ),
        WelcomeStarterOption(
            title = "Ask anything",
            subtitle = "Get verified real-time answers, facts, or technical insights",
            prompt = "What are some intriguing topics or questions we can explore today?",
            icon = Icons.Outlined.AutoAwesome,
            badge = "Explore",
            accentColor = MayraCyan
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("empty_chat_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Center 3D-Style Mayra Welcome Avatar with subtle floating & glow
        Box(
            modifier = Modifier
                .size(76.dp)
                .graphicsLayer { translationY = floatY },
            contentAlignment = Alignment.Center
        ) {
            // Ambient Radial Glow
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .graphicsLayer {
                        scaleX = glowScale
                        scaleY = glowScale
                    }
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MayraCyanBright.copy(alpha = 0.35f),
                                MayraIndigo.copy(alpha = 0.20f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // Sleek Metallic Badge
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF1E293B),
                                MayraDarkSurface,
                                Color(0xFF020617)
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            listOf(MayraCyanBright, MayraIndigo, MayraViolet)
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "M",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MayraCyanBright
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Headline: "How can Mayra help you today?"
        Text(
            text = "How can Mayra help you today?",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                fontSize = 24.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("welcome_heading")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle: Multilingual readiness & prompt starter hint
        Text(
            text = "Select a quick language option below or type your message in any language.",
            style = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 20.sp,
                fontSize = 13.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Simple, Clean Starter Options List
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            starterOptions.forEachIndexed { index, option ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MayraDarkSurfaceElevated.copy(alpha = 0.75f))
                        .border(
                            width = 1.dp,
                            color = MayraDarkSurfaceBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSuggestionClicked(option.prompt) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("starter_option_$index")
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
                            // Rounded Icon Container
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MayraDarkSurface)
                                    .border(
                                        width = 0.8.dp,
                                        color = option.accentColor.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = option.title,
                                    tint = option.accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Title & Description
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = option.subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Category Badge Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(option.accentColor.copy(alpha = 0.12f))
                                .border(
                                    width = 0.5.dp,
                                    color = option.accentColor.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = option.badge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = option.accentColor
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}
