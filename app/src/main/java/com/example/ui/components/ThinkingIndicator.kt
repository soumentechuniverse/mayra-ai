package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.SearchPhase
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraCyanBright
import com.example.ui.theme.MayraDarkSurfaceBorder
import com.example.ui.theme.MayraDarkSurfaceElevated
import com.example.ui.theme.MayraIndigo
import com.example.ui.theme.MayraViolet

/**
 * Animated 'Mayra is thinking...' typing indicator for the chat stream.
 *
 * Provides real-time visual feedback while waiting for AI responses:
 * - Three bouncing, phase-delayed typing dots
 * - Glowing avatar with breathing halo animation
 * - Fluid status text ("Mayra is thinking...")
 * - Web search phase transitions (Searching the web..., Reading sources...)
 */
@Composable
fun ThinkingIndicator(
    searchPhase: SearchPhase = SearchPhase.IDLE,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots_transition")

    // Vertical bouncing offset for typing dots (-5.dp to 0.dp)
    val dotOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_offset_1"
    )

    val dotOffset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_offset_2"
    )

    val dotOffset3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_offset_3"
    )

    // Pulsing alpha for each dot
    val dotAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha_1"
    )

    val dotAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha_2"
    )

    val dotAlpha3 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha_3"
    )

    // Glowing avatar halo scale and opacity
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_scale"
    )

    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_alpha"
    )

    // Breathing text alpha for "Mayra is thinking..."
    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "text_alpha"
    )

    val isSearching = searchPhase == SearchPhase.SEARCHING || searchPhase == SearchPhase.READING_SOURCES
    val statusText = when (searchPhase) {
        SearchPhase.SEARCHING -> "Searching the web..."
        SearchPhase.READING_SOURCES -> "Reading sources..."
        SearchPhase.GENERATING -> "Mayra is thinking..."
        SearchPhase.IDLE -> "Mayra is thinking..."
    }

    Row(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                )
            )
            .background(MayraDarkSurfaceElevated.copy(alpha = 0.85f))
            .border(
                1.dp,
                if (isSearching) MayraCyan.copy(alpha = 0.5f) else MayraDarkSurfaceBorder,
                RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                )
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("thinking_indicator")
            .semantics {
                contentDescription = statusText
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Glowing Avatar
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pulsing halo ring
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .scale(haloScale)
                    .clip(CircleShape)
                    .background(MayraCyan.copy(alpha = haloAlpha * 0.3f))
            )

            if (isSearching) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MayraCyan.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (searchPhase == SearchPhase.SEARCHING) Icons.Outlined.Search else Icons.Outlined.Public,
                        contentDescription = null,
                        tint = MayraCyan,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(MayraCyan, MayraIndigo))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "M",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // "Mayra is thinking..." animated text
        AnimatedContent(
            targetState = statusText,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "status_text_anim"
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = (if (isSearching) MayraCyanBright else MaterialTheme.colorScheme.onSurface).copy(alpha = textAlpha),
                modifier = Modifier.testTag("thinking_status_text")
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Three animated rhythmic bouncing typing dots
        Row(
            modifier = Modifier
                .padding(bottom = 1.dp)
                .testTag("typing_indicator_dots"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .offset(y = dotOffset1.dp)
                    .size(6.dp)
                    .alpha(dotAlpha1)
                    .clip(CircleShape)
                    .background(MayraCyan)
            )
            Box(
                modifier = Modifier
                    .offset(y = dotOffset2.dp)
                    .size(6.dp)
                    .alpha(dotAlpha2)
                    .clip(CircleShape)
                    .background(MayraIndigo)
            )
            Box(
                modifier = Modifier
                    .offset(y = dotOffset3.dp)
                    .size(6.dp)
                    .alpha(dotAlpha3)
                    .clip(CircleShape)
                    .background(MayraViolet)
            )
        }
    }
}
