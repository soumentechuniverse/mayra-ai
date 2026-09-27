package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MayraDarkColorScheme = darkColorScheme(
    primary = MayraCyan,
    onPrimary = Color(0xFF003548),
    primaryContainer = MayraCyanContainer,
    onPrimaryContainer = MayraCyanOnContainer,

    secondary = MayraIndigo,
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = MayraIndigoContainer,
    onSecondaryContainer = Color(0xFFC7D2FE),

    tertiary = MayraEmerald,
    onTertiary = Color(0xFF022C22),
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = Color(0xFFA7F3D0),

    background = MayraDarkBg,
    onBackground = MayraTextPrimaryDark,

    surface = MayraDarkSurface,
    onSurface = MayraTextPrimaryDark,
    surfaceVariant = MayraDarkSurfaceElevated,
    onSurfaceVariant = MayraTextSecondaryDark,

    outline = MayraDarkSurfaceBorder,
    outlineVariant = MayraDarkSurfaceHover,

    error = MayraRose,
    onError = Color.White
)

private val MayraLightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),

    secondary = Color(0xFF4F46E5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEF2FF),
    onSecondaryContainer = Color(0xFF3730A3),

    tertiary = Color(0xFF059669),
    onTertiary = Color.White,

    background = MayraLightBg,
    onBackground = MayraTextPrimaryLight,

    surface = MayraLightSurface,
    onSurface = MayraTextPrimaryLight,
    surfaceVariant = MayraLightSurfaceElevated,
    onSurfaceVariant = MayraTextSecondaryLight,

    outline = MayraLightSurfaceBorder,
    outlineVariant = Color(0xFFCBD5E1),

    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun MayraAITheme(
    darkTheme: Boolean = true, // Premium Dark-First default
    dynamicColor: Boolean = false, // Preserve Mayra's signature cosmic dark identity
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> MayraDarkColorScheme
        else -> MayraLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
