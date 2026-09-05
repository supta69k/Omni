package com.example.omni.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Omni uses a single fixed light scheme.
 *
 * Dynamic colour (the Material You wallpaper extraction the project template ships with) is
 * deliberately disabled: it would repaint the pastel Soft-Bento palette with whatever the user's
 * wallpaper happens to be, which is exactly the anxiety-free visual identity the design depends on.
 * Dark theme is likewise not wired up yet — the Figma source only defines light frames, so
 * inventing a dark palette here would drift from the design.
 */
private val OmniColorScheme = lightColorScheme(
    primary = OmniInk,
    onPrimary = OmniOnInk,
    secondary = OmniLavender,
    tertiary = OmniPink,
    background = OmniBackground,
    onBackground = OmniInk,
    surface = OmniBackground,
    onSurface = OmniInk,
)

@Composable
fun OmniTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OmniColorScheme,
        typography = Typography,
        content = content,
    )
}
