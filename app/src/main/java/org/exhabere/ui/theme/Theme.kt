package org.exhabere.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ExHabereColors = darkColorScheme(
    primary = Mint300,
    onPrimary = Slate900,
    primaryContainer = Slate700,
    onPrimaryContainer = TextPrimary,
    secondary = Mint400,
    onSecondary = Slate900,
    background = Slate900,
    onBackground = TextPrimary,
    surface = Slate800,
    onSurface = TextPrimary,
    surfaceVariant = Slate700,
    onSurfaceVariant = TextSecondary,
    outline = Slate500,
    error = Emergency,
    onError = EmergencyContent,
    surfaceTint = Color.Transparent
)

/** Consistent, quiet colors and typography for the main app and the system overlay. */
@Composable
fun ExHabereTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ExHabereColors,
        typography = ExHabereTypography,
        content = content
    )
}
