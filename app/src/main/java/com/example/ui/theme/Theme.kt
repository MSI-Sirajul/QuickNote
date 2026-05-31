package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF003F6C),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFCE93D8),
    onSecondary = Color(0xFF4A148C),
    secondaryContainer = Color(0xFF3B0068),
    onSecondaryContainer = Color(0xFFF3E8FF),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0x331E293B), // Translucent slate
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0x1F475569),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0x33FFFFFF), // Translucent border highlight
    error = Color(0xFFEF4444)
)

// Helper mock constant to keep light colors distinct
private val OnSecondaryContainerMock = Color(0xFF21005D)

private val LightColorScheme = lightColorScheme(
    primary = GlassPrimary,
    onPrimary = Color.White,
    primaryContainer = GlassActivePill,
    onPrimaryContainer = Color(0xFF001D35),
    secondary = GlassVoiceBtn,
    onSecondary = Color.White,
    secondaryContainer = GlassVoiceBtnContainer,
    onSecondaryContainer = OnSecondaryContainerMock,
    background = GlassBgBase,
    onBackground = GlassTextHigh,
    surface = GlassSurfaceTranslucent,
    onSurface = GlassTextHigh,
    surfaceVariant = Color(0x4DFFFFFF), // 30% translucent white
    onSurfaceVariant = GlassTextMed,
    outline = GlassTextLow,
    outlineVariant = GlassBorderHighlight,
    error = GlassAccentRed
)

@Composable
fun GlassyBackdrop(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (darkTheme) Color(0xFF0F172A) else Color(0xFFF0F4F9))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (darkTheme) {
                // Soft glowing orbs for dark mode
                drawCircle(
                    color = Color(0xFF003F6C).copy(alpha = 0.4f),
                    radius = size.width * 0.7f,
                    center = Offset(size.width * 0.9f, size.height * 0.15f)
                )
                drawCircle(
                    color = Color(0xFF3B0068).copy(alpha = 0.35f),
                    radius = size.width * 0.6f,
                    center = Offset(size.width * 0.1f, size.height * 0.85f)
                )
            } else {
                // Soft pastel color blobs for light mode
                drawCircle(
                    color = Color(0xFFD3E3FD).copy(alpha = 0.8f),
                    radius = size.width * 0.7f,
                    center = Offset(size.width * 0.9f, size.height * 0.15f)
                )
                drawCircle(
                    color = Color(0xFFF3E8FF).copy(alpha = 0.8f),
                    radius = size.width * 0.6f,
                    center = Offset(size.width * 0.1f, size.height * 0.85f)
                )
            }
        }
        content()
    }
}

@Composable
fun MyApplicationTheme(
    themeMode: com.example.data.ThemeMode = com.example.data.ThemeMode.SYSTEM,
    dynamicColor: Boolean = false, // Set to false to enforce our signature visual style consistently
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        com.example.data.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        com.example.data.ThemeMode.LIGHT -> false
        com.example.data.ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = {
            GlassyBackdrop(darkTheme = darkTheme, content = content)
        }
    )
}
