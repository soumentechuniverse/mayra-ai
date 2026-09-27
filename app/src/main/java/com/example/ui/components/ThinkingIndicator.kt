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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.SearchPhase
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraCyanBright
import com.example.ui.theme.MayraDarkSurfaceBorder
import com.example.ui.theme.MayraDarkSurfaceElevated
import com.example.ui.theme.MayraIndigo
import com.example.ui.theme.MayraViolet

@Composable
fun ThinkingIndicator(
    searchPhase: SearchPhase = SearchPhase.IDLE,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking_transition")

    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_1"
    )

    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_2"
    )

    val pulseScale3 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_3"
    )

    val isSearching = searchPhase == SearchPhase.SEARCHING || searchPhase == SearchPhase.READING_SOURCES
    val statusText = when (searchPhase) {
        SearchPhase.SEARCHING -> "Searching the web…"
        SearchPhase.READING_SOURCES -> "Reading sources…"
        SearchPhase.GENERATING -> "Generating answer…"
        SearchPhase.IDLE -> "Mayra is thinking"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MayraDarkSurfaceElevated)
            .border(
                1.dp,
                if (isSearching) MayraCyan.copy(alpha = 0.5f) else MayraDarkSurfaceBorder,
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("thinking_indicator"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon / Avatar
        if (isSearching) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MayraCyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (searchPhase == SearchPhase.SEARCHING) Icons.Outlined.Search else Icons.Outlined.Public,
                    contentDescription = null,
                    tint = MayraCyan,
                    modifier = Modifier.size(15.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(MayraCyan, MayraIndigo))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "M",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        AnimatedContent(
            targetState = statusText,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "status_text_anim"
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSearching) MayraCyanBright else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Three animated rhythmic pulsing dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .scale(pulseScale1)
                    .clip(CircleShape)
                    .background(MayraCyan)
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .scale(pulseScale2)
                    .clip(CircleShape)
                    .background(MayraIndigo)
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .scale(pulseScale3)
                    .clip(CircleShape)
                    .background(MayraViolet)
            )
        }
    }
}
