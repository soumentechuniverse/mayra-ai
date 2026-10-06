package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MayraCyan
import com.example.ui.theme.MayraDarkBorder
import com.example.ui.theme.MayraDarkSurface
import com.example.ui.theme.MayraIndigo
import com.example.ui.theme.MayraTextSecondary
import kotlinx.coroutines.delay

@Composable
fun ThinkingIndicator(
    modifier: Modifier = Modifier
) {
    val dot1Offset = remember { Animatable(0f) }
    val dot2Offset = remember { Animatable(0f) }
    val dot3Offset = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val animationSpec = infiniteRepeatable<Float>(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )

        dot1Offset.animateTo(targetValue = -6f, animationSpec = animationSpec)
    }

    LaunchedEffect(Unit) {
        delay(150)
        val animationSpec = infiniteRepeatable<Float>(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
        dot2Offset.animateTo(targetValue = -6f, animationSpec = animationSpec)
    }

    LaunchedEffect(Unit) {
        delay(300)
        val animationSpec = infiniteRepeatable<Float>(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
        dot3Offset.animateTo(targetValue = -6f, animationSpec = animationSpec)
    }

    Row(
        modifier = modifier
            .testTag("thinking_indicator")
            .clip(RoundedCornerShape(16.dp))
            .background(MayraDarkSurface)
            .border(1.dp, MayraDarkBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MayraCyan,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Mayra is thinking...",
            color = MayraTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 3 Animated Bouncing Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .offset(y = dot1Offset.value.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MayraCyan)
            )
            Box(
                modifier = Modifier
                    .offset(y = dot2Offset.value.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MayraIndigo)
            )
            Box(
                modifier = Modifier
                    .offset(y = dot3Offset.value.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MayraCyan)
            )
        }
    }
}
