package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
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
import kotlinx.coroutines.delay

@Composable
fun StartupScreen(
    onStartupFinished: () -> Unit,
    modifier: Modifier = Modifier,
    durationMillis: Long = 10_000L
) {
    val density = LocalDensity.current.density
    val interactionSource = remember { MutableInteractionSource() }

    // Smooth entry animations for content layers
    val contentAlpha = remember { Animatable(0f) }
    val contentScale = remember { Animatable(0.9f) }
    val progress = remember { Animatable(0f) }

    // Continuous 3D floating and glowing animations
    val infiniteTransition = rememberInfiniteTransition(label = "MayraLogo3DTransition")

    // Gentle 3D floating translation Y
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatingY"
    )

    // Gentle 3D tilt Y (perspective rotation)
    val animatedRotY by infiniteTransition.animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotationY"
    )

    // Gentle 3D tilt X
    val animatedRotX by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotationX"
    )

    // Gentle rotation Z for orbital harmony
    val animatedRotZ by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotationZ"
    )

    // Breathing glow intensity
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.80f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Orbital ring continuous rotation
    val orbitalAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitalAngle"
    )

    // Animate progress and automatically trigger finish after approximately 10 seconds
    LaunchedEffect(Unit) {
        contentAlpha.animateTo(1f, animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing))
        contentScale.animateTo(1f, animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMillis.toInt(), easing = LinearEasing)
        )
        onStartupFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF080D1A),
                        MayraDarkBg
                    ),
                    center = Offset(Float.POSITIVE_INFINITY / 2f, 800f),
                    radius = 1600f
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                // Allows user to smoothly skip directly into chat if they wish
                onStartupFinished()
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("startup_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Decorative ambient background mesh
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.12f)) {
            val width = size.width
            val height = size.height
            drawCircle(
                brush = Brush.radialGradient(listOf(MayraCyanBright, Color.Transparent)),
                radius = width * 0.5f,
                center = Offset(width * 0.2f, height * 0.25f)
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(MayraViolet, Color.Transparent)),
                radius = width * 0.6f,
                center = Offset(width * 0.8f, height * 0.65f)
            )
        }

        // Center Content Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    alpha = contentAlpha.value
                    scaleX = contentScale.value
                    scaleY = contentScale.value
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 3D-Style Mayra Logo Container with floating, tilting, and glowing
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .graphicsLayer {
                        translationY = floatOffset
                        rotationX = animatedRotX
                        rotationY = animatedRotY
                        rotationZ = animatedRotZ
                        cameraDistance = 18f * density
                    },
                contentAlignment = Alignment.Center
            ) {
                // Pulsing Radial Ambient Glow behind logo
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .graphicsLayer {
                            scaleX = glowScale
                            scaleY = glowScale
                            alpha = glowAlpha
                        }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MayraCyanBright.copy(alpha = 0.55f),
                                    MayraIndigo.copy(alpha = 0.35f),
                                    MayraViolet.copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // 3D Orbital Rings
                Canvas(
                    modifier = Modifier
                        .size(180.dp)
                        .graphicsLayer {
                            rotationZ = orbitalAngle
                        }
                ) {
                    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                MayraCyanBright.copy(alpha = 0.8f),
                                MayraIndigo.copy(alpha = 0.2f),
                                MayraViolet.copy(alpha = 0.7f),
                                Color.Transparent,
                                MayraCyanBright.copy(alpha = 0.8f)
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 280f,
                        useCenter = false,
                        style = stroke
                    )
                }

                // 3D Metallic / Glassmorphic Mayra Emblem
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .shadow(
                            elevation = 28.dp,
                            shape = RoundedCornerShape(32.dp),
                            spotColor = MayraCyanBright,
                            ambientColor = MayraIndigo
                        )
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF1E293B),
                                    Color(0xFF0F172A),
                                    Color(0xFF020617)
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MayraCyanBright,
                                    MayraIndigo,
                                    MayraViolet,
                                    MayraCyan.copy(alpha = 0.5f)
                                )
                            ),
                            shape = RoundedCornerShape(32.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Internal 3D Logo Monogram Geometry (Sculpted "M" with Specular Shading)
                    Canvas(modifier = Modifier.size(76.dp)) {
                        val w = size.width
                        val h = size.height

                        // Left vertical ribbon
                        val pathLeft = Path().apply {
                            moveTo(w * 0.15f, h * 0.82f)
                            lineTo(w * 0.15f, h * 0.24f)
                            cubicTo(w * 0.15f, h * 0.16f, w * 0.25f, h * 0.16f, w * 0.28f, h * 0.22f)
                            lineTo(w * 0.44f, h * 0.56f)
                            lineTo(w * 0.34f, h * 0.64f)
                            lineTo(w * 0.24f, h * 0.44f)
                            lineTo(w * 0.24f, h * 0.82f)
                            close()
                        }
                        drawPath(
                            path = pathLeft,
                            brush = Brush.linearGradient(
                                colors = listOf(MayraCyanBright, MayraCyan, MayraIndigo)
                            )
                        )

                        // Right vertical ribbon
                        val pathRight = Path().apply {
                            moveTo(w * 0.85f, h * 0.82f)
                            lineTo(w * 0.85f, h * 0.24f)
                            cubicTo(w * 0.85f, h * 0.16f, w * 0.75f, h * 0.16f, w * 0.72f, h * 0.22f)
                            lineTo(w * 0.56f, h * 0.56f)
                            lineTo(w * 0.66f, h * 0.64f)
                            lineTo(w * 0.76f, h * 0.44f)
                            lineTo(w * 0.76f, h * 0.82f)
                            close()
                        }
                        drawPath(
                            path = pathRight,
                            brush = Brush.linearGradient(
                                colors = listOf(MayraViolet, MayraIndigo, MayraCyanBright)
                            )
                        )

                        // Center 3D apex diamond crest
                        val apexPath = Path().apply {
                            moveTo(w * 0.50f, h * 0.42f)
                            lineTo(w * 0.62f, h * 0.58f)
                            lineTo(w * 0.50f, h * 0.76f)
                            lineTo(w * 0.38f, h * 0.58f)
                            close()
                        }
                        drawPath(
                            path = apexPath,
                            brush = Brush.linearGradient(
                                colors = listOf(MayraCyanBright, MayraViolet)
                            )
                        )

                        // Specular lighting highlight glint
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White, Color.Transparent),
                                radius = 9.dp.toPx()
                            ),
                            radius = 6.dp.toPx(),
                            center = Offset(w * 0.28f, h * 0.24f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Text: Mayra AI (Prominent, Elegant Typography)
            Text(
                text = "Mayra AI",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    fontSize = 38.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("startup_title")
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Subtitle Divider Line
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, MayraCyanBright, Color.Transparent)
                        )
                    )
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Text: Created & Published by
            Text(
                text = "Created & Published by",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 1.2.sp,
                    fontSize = 14.sp
                ),
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("startup_created_by")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Text: Soumen Mondal (Creator Attribution)
            Text(
                text = "Soumen Mondal",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontSize = 18.sp
                ),
                color = MayraCyanBright,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("startup_author")
            )

            Spacer(modifier = Modifier.height(52.dp))

            // Loading / Progress Animation Section
            Column(
                modifier = Modifier.fillMaxWidth(0.72f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Sleek glowing progress track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MayraDarkSurfaceBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.value)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(MayraCyan, MayraCyanBright, MayraIndigo, MayraViolet)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Progress status hint with skip indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Starting Mayra AI...",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color(0xFF64748B)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    ) {
                        Text(
                            text = "Tap to enter",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MayraCyan.copy(alpha = 0.85f)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = "Enter",
                            tint = MayraCyan.copy(alpha = 0.85f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
