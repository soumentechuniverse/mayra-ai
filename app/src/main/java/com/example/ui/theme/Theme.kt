package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = MayraCyan,
    onPrimary = MayraDarkBackground,
    primaryContainer = MayraIndigoDark,
    onPrimaryContainer = MayraCyanLight,
    secondary = MayraIndigo,
    onSecondary = MayraTextPrimary,
    background = MayraDarkBackground,
    onBackground = MayraTextPrimary,
    surface = MayraDarkSurface,
    onSurface = MayraTextPrimary,
    surfaceVariant = MayraDarkSurfaceVariant,
    onSurfaceVariant = MayraTextSecondary,
    outline = MayraDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = MayraIndigo,
    onPrimary = MayraTextPrimary,
    primaryContainer = MayraIndigoDark,
    onPrimaryContainer = MayraCyanLight,
    secondary = MayraPurple,
    onSecondary = MayraTextPrimary,
    background = MayraDarkBackground,
    onBackground = MayraTextPrimary,
    surface = MayraDarkSurface,
    onSurface = MayraTextPrimary,
    surfaceVariant = MayraDarkSurfaceVariant,
    onSurfaceVariant = MayraTextSecondary,
    outline = MayraDarkBorder
)

@Composable
fun MayraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MayraAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) = MayraTheme(darkTheme = darkTheme, content = content)

