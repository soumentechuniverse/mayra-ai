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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraCyanBright
import com.example.ui.theme.MayraDarkSurface
import com.example.ui.theme.MayraDarkSurfaceBorder
import com.example.ui.theme.MayraDarkSurfaceElevated
import com.example.ui.theme.MayraIndigo
import com.example.ui.theme.MayraViolet

private data class SimpleLanguageOption(
    val name: String,
    val prompt: String,
    val accentColor: Color
)

@Composable
fun EmptyChatView(
    onSuggestionClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Subtle breathing animation for Mayra welcome emblem
    val infiniteTransition = rememberInfiniteTransition(label = "WelcomeEmblemTransition")
    val floatY by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    // 3 Simple Language Options
    val languageOptions = listOf(
        SimpleLanguageOption(
            name = "বাংলা",
            prompt = "নমস্কার Mayra! আমি বাংলায় কথা বলতে চাই।",
            accentColor = MayraCyan
        ),
        SimpleLanguageOption(
            name = "English",
            prompt = "Hello Mayra! Let's get started in English.",
            accentColor = MayraCyanBright
        ),
        SimpleLanguageOption(
            name = "हिंदी",
            prompt = "नमस्ते Mayra! चलिए हिंदी में बातचीत शुरू करते हैं।",
            accentColor = MayraIndigo
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("empty_chat_view"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Modern Centered Mayra AI Logo
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
                                    MayraIndigo.copy(alpha = 0.18f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Sleek Metallic Monogram Badge
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF1E293B),
                                    MayraDarkSurface,
                                    Color(0xFF030712)
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

            Spacer(modifier = Modifier.height(20.dp))

            // Headline: "How can Mayra help you today?"
            Text(
                text = "How can Mayra help you today?",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    fontSize = 23.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("welcome_heading")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Clean, understated subhead
            Text(
                text = "Choose a language or ask anything below",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Clean 3 Simple Language Options (Row of modern tactile pills)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("language_options_row"),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                languageOptions.forEachIndexed { index, option ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MayraDarkSurfaceElevated.copy(alpha = 0.85f))
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    listOf(
                                        option.accentColor.copy(alpha = 0.45f),
                                        MayraDarkSurfaceBorder
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onSuggestionClicked(option.prompt) }
                            .padding(vertical = 14.dp, horizontal = 8.dp)
                            .testTag("starter_option_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                letterSpacing = 0.2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
