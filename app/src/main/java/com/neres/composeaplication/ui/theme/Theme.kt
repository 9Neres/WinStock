package com.neres.composeaplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.Typography

private val DarkColors = darkColorScheme(
    primary = ModernPurple,
    secondary = CustomGreen,
    tertiary = WarningYellow,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = TextOnDark,
    onSecondary = TextOnDark,
    onTertiary = TextOnDark,
    onBackground = TextOnDark,
    onSurface = TextOnDark,
    error = CustomRed,
    secondaryContainer = CustomGreen.copy(alpha = 0.1f),
    primaryContainer = ModernPurple.copy(alpha = 0.1f),
    surfaceVariant = DarkSurface.copy(alpha = 0.3f),
    errorContainer = CustomRed.copy(alpha = 0.1f),
    tertiaryContainer = WarningYellow.copy(alpha = 0.1f),
    onPrimaryContainer = ModernPurple,
    onSecondaryContainer = CustomGreen,
    onTertiaryContainer = WarningYellow,
    onErrorContainer = CustomRed
)

@Composable
fun ComposeApplicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content
    )
}