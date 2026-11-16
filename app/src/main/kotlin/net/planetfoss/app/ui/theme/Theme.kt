// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Colours of the PlanetFOSS website (public/assets/site.css): space (background),
 * moon (light background), nebula (accent), star (supernova), mars (error); surface-2 (#1C2449)
 * and accent-soft (#ECE8FF / #2A2363) as in the light and dark variables there.
 * No dynamic colour by default – the app should look like PlanetFOSS.
 */
private val Space950 = Color(0xFF050A24)
private val Space900 = Color(0xFF0B1026)
private val Space800 = Color(0xFF141A3A)
private val Space700 = Color(0xFF232B55)
private val Moon50 = Color(0xFFF5F6FB)
private val Moon200 = Color(0xFFDCE0EE)
private val Moon500 = Color(0xFF8F97B5)
private val Muted = Color(0xFF586079)
private val Nebula300 = Color(0xFFB3A4FF)
private val Nebula500 = Color(0xFF6E5BEB)
private val Accent = Color(0xFF5A45E6)
private val Star = Color(0xFFFCC954)
private val Mars = Color(0xFFFF6B4A)

private val Light = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E0FF),
    onPrimaryContainer = Color(0xFF1B0C6B),
    // Material colours selected tabs and filter chips with `secondary`: the website uses its accent
    // and --accent-soft there, so both schemes keep the teal aurora out of the navigation
    secondary = Accent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE8FF),
    onSecondaryContainer = Color(0xFF1B0C6B),
    tertiary = Color(0xFF7A5600),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8B3),
    onTertiaryContainer = Color(0xFF261900),
    background = Moon50,
    onBackground = Space900,
    surface = Moon50,
    onSurface = Space900,
    surfaceVariant = Moon200,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEDEFF7),
    surfaceContainerHigh = Color(0xFFE7E9F3),
    surfaceContainerHighest = Moon200,
    outline = Color(0xFF8F97B5),
    outlineVariant = Moon200,
    error = Color(0xFFC2401F),
    onError = Color.White,
)

private val Dark = darkColorScheme(
    primary = Nebula300,
    onPrimary = Color(0xFF1B0C6B),
    primaryContainer = Color(0xFF3A2A99),
    onPrimaryContainer = Color(0xFFE6E0FF),
    secondary = Nebula300,
    onSecondary = Color(0xFF1B0C6B),
    secondaryContainer = Color(0xFF2A2363),
    onSecondaryContainer = Color(0xFFE6E0FF),
    tertiary = Star,
    onTertiary = Color(0xFF1A1300),
    tertiaryContainer = Color(0xFF4D3A00),
    onTertiaryContainer = Color(0xFFFFE8B3),
    background = Space900,
    onBackground = Moon50,
    surface = Space900,
    onSurface = Moon50,
    surfaceVariant = Space700,
    onSurfaceVariant = Moon500,
    surfaceContainerLowest = Space950,
    surfaceContainerLow = Space800,
    surfaceContainer = Space800,
    surfaceContainerHigh = Color(0xFF1C2449),
    surfaceContainerHighest = Space700,
    outline = Color(0xFF5A638A),
    outlineVariant = Space700,
    error = Mars,
    onError = Color(0xFF3A0B00),
)

/**
 * Colours outside the Material scheme. aurora (the website's --aurora): a source that recommends another
 * project instead of asking for support itself – set apart from the accent of “Support <source>”.
 */
@Immutable
data class PlanetColors(
    val supernova: Color,
    val onSupernova: Color,
    val nebula: Color,
    val aurora: Color = AuroraLight,
    val onAurora: Color = Color.White,
    val auroraContainer: Color = Color(0xFFD7F0EB),
    val onAuroraContainer: Color = Color(0xFF00201C),
    /** Comets (`.burst-komet`): the light star colour. */
    val comet: Color = Color(0xFFFFE9A8),
    /** The website's violet (`--violet`): the edge of the intro hint. */
    val violet: Color = Color(0xFF6E5BEB),
    /** The night sky of the website's header (`--head`), in light and dark mode alike. */
    val night: Color = Space950,
)

private val AuroraLight = Color(0xFF0B7A6E)
private val AuroraDark = Color(0xFF5EE4DF)

val LocalPlanetColors = staticCompositionLocalOf { PlanetColors(Star, Color(0xFF1A1300), Nebula500) }

/** Material 3 Expressive with the PlanetFOSS colours, fonts and expressive motion. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlanetFossTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialExpressiveTheme(colorScheme = if (dark) Dark else Light, motionScheme = MotionScheme.expressive(), typography = PlanetTypography) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalPlanetColors provides if (dark) {
                PlanetColors(Star, Color(0xFF1A1300), Nebula500, aurora = AuroraDark, onAurora = Space900,
                    auroraContainer = Color(0xFF0F3B36), onAuroraContainer = Color(0xFFC8F5EE))
            } else {
                PlanetColors(Star, Color(0xFF1A1300), Nebula500)
            },
            content = content,
        )
    }
}
