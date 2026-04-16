package com.opensmarthome.speaker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    background = SpeakerBackground,
    surface = SpeakerSurface,
    surfaceVariant = SpeakerSurfaceVariant,
    primary = SpeakerPrimary,
    onPrimary = SpeakerOnPrimary,
    onBackground = SpeakerTextPrimary,
    onSurface = SpeakerTextPrimary,
    onSurfaceVariant = SpeakerTextSecondary,
    primaryContainer = SpeakerSurfaceElevated,
    onPrimaryContainer = SpeakerTextPrimary,
    secondaryContainer = SpeakerSurfaceElevated,
    onSecondaryContainer = SpeakerTextPrimary,
    error = VoiceError,
    onError = SpeakerTextPrimary
)

// Light palette for kitchen-daylight / accessibility high-contrast use.
// The smart-speaker aesthetic is primarily dark; ambient/home screens use
// hard-coded Speaker* colors and intentionally stay dark regardless, but
// Material3 surfaces (Card, TopAppBar, Dialog, Settings screens) respect
// the chosen scheme.
private val LightColorScheme = lightColorScheme(
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDEEF1),
    primary = SpeakerPrimary,
    onPrimary = Color.White,
    onBackground = Color(0xFF0D1117),
    onSurface = Color(0xFF0D1117),
    onSurfaceVariant = Color(0xFF484F58),
    primaryContainer = Color(0xFFDCE7F6),
    onPrimaryContainer = Color(0xFF0D1117),
    secondaryContainer = Color(0xFFE8ECF1),
    onSecondaryContainer = Color(0xFF0D1117),
    error = VoiceError,
    onError = Color.White
)

@Composable
fun OpenSmartSpeakerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
