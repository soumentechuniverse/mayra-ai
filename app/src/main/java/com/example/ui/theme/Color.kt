package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Mayra AI Deep Obsidian / Cosmic Navy Palette
val MayraDarkBg = Color(0xFF070A12)
val MayraDarkSurface = Color(0xFF0D1424)
val MayraDarkSurfaceElevated = Color(0xFF141D33)
val MayraDarkSurfaceBorder = Color(0xFF1F2B48)
val MayraDarkSurfaceHover = Color(0xFF19253F)

// Accents
val MayraCyan = Color(0xFF38BDF8)
val MayraCyanBright = Color(0xFF00E5FF)
val MayraCyanContainer = Color(0xFF0C2B45)
val MayraCyanOnContainer = Color(0xFFBAE6FD)

val MayraIndigo = Color(0xFF818CF8)
val MayraIndigoContainer = Color(0xFF1E1B4B)
val MayraViolet = Color(0xFFA855F7)
val MayraEmerald = Color(0xFF10B981)
val MayraAmber = Color(0xFFF59E0B)
val MayraRose = Color(0xFFF43F5E)

// Text & Content Colors - Dark
val MayraTextPrimaryDark = Color(0xFFF1F5F9)
val MayraTextSecondaryDark = Color(0xFF94A3B8)
val MayraTextMutedDark = Color(0xFF64748B)

// Light Palette (for theme architecture readiness)
val MayraLightBg = Color(0xFFF8FAFC)
val MayraLightSurface = Color(0xFFFFFFFF)
val MayraLightSurfaceElevated = Color(0xFFF1F5F9)
val MayraLightSurfaceBorder = Color(0xFFE2E8F0)
val MayraTextPrimaryLight = Color(0xFF0F172A)
val MayraTextSecondaryLight = Color(0xFF475569)
val MayraTextMutedLight = Color(0xFF94A3B8)

// Code Block Palette
val MayraCodeBg = Color(0xFF090D17)
val MayraCodeBorder = Color(0xFF1E293B)
val MayraCodeText = Color(0xFF38BDF8)

// Gradients
val MayraBrandGradient = Brush.linearGradient(
    colors = listOf(MayraCyanBright, MayraIndigo, MayraViolet)
)

val MayraSurfaceGradient = Brush.verticalGradient(
    colors = listOf(MayraDarkSurfaceElevated, MayraDarkSurface)
)

val MayraCardBorderGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.4f), Color(0xFF818CF8).copy(alpha = 0.2f), Color.Transparent)
)
