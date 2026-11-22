package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class RadarSemanticPalette(
    val chassisBg: Color,
    val cardBorder: Color,
    val inRange: Color,
    val inRangeContainer: Color,
    val weakUnstable: Color,
    val weakUnstableContainer: Color,
    val outOfRange: Color,
    val outOfRangeContainer: Color,
    val relayAccent: Color,
    val relayContainer: Color,
    val pttAccent: Color,
    val mutedText: Color,
    val isDark: Boolean
)

private val DarkRadarSemantics = RadarSemanticPalette(
    chassisBg = RadarChassis,
    cardBorder = RadarBorder,
    inRange = SignalGreen,
    inRangeContainer = SignalGreenContainer,
    weakUnstable = SignalYellow,
    weakUnstableContainer = SignalYellowContainer,
    outOfRange = SignalRed,
    outOfRangeContainer = SignalRedContainer,
    relayAccent = RelayCyan,
    relayContainer = RelayCyanContainer,
    pttAccent = PttOrange,
    mutedText = TextMuted,
    isDark = true
)

private val LightRadarSemantics = RadarSemanticPalette(
    chassisBg = DaylightSurfaceVariant,
    cardBorder = DaylightBorder,
    inRange = DaylightGreen,
    inRangeContainer = DaylightGreenContainer,
    weakUnstable = DaylightYellow,
    weakUnstableContainer = DaylightYellowContainer,
    outOfRange = DaylightRed,
    outOfRangeContainer = DaylightRedContainer,
    relayAccent = DaylightCyan,
    relayContainer = DaylightCyanContainer,
    pttAccent = PttOrange,
    mutedText = DaylightTextSecondary,
    isDark = false
)

val LocalRadarColors = staticCompositionLocalOf { DarkRadarSemantics }

private val TacticalDarkColorScheme = darkColorScheme(
    primary = SignalGreen,
    onPrimary = ObsidianBg,
    primaryContainer = SignalGreenContainer,
    onPrimaryContainer = SignalGreen,
    secondary = RelayCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = RelayCyanContainer,
    onSecondaryContainer = RelayCyan,
    tertiary = SignalYellow,
    onTertiary = ObsidianBg,
    tertiaryContainer = SignalYellowContainer,
    onTertiaryContainer = SignalYellow,
    error = SignalRed,
    onError = Color.White,
    errorContainer = SignalRedContainer,
    onErrorContainer = SignalRed,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = RadarSurface,
    onSurface = TextPrimary,
    surfaceVariant = RadarSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = RadarBorder,
    outlineVariant = RadarChassis
)

private val TacticalLightColorScheme = lightColorScheme(
    primary = DaylightGreen,
    onPrimary = Color.White,
    primaryContainer = DaylightGreenContainer,
    onPrimaryContainer = DaylightGreen,
    secondary = DaylightCyan,
    onSecondary = Color.White,
    secondaryContainer = DaylightCyanContainer,
    onSecondaryContainer = DaylightCyan,
    tertiary = DaylightYellow,
    onTertiary = Color.White,
    tertiaryContainer = DaylightYellowContainer,
    onTertiaryContainer = DaylightYellow,
    error = DaylightRed,
    onError = Color.White,
    errorContainer = DaylightRedContainer,
    onErrorContainer = DaylightRed,
    background = DaylightBg,
    onBackground = DaylightTextPrimary,
    surface = DaylightSurface,
    onSurface = DaylightTextPrimary,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = DaylightTextSecondary,
    outline = DaylightBorder,
    outlineVariant = DaylightSurfaceVariant
)

@Composable
fun WalkieTalkieProTheme(
    darkTheme: Boolean = true, // Tactical radio/radar defaults to high-contrast dark console
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TacticalDarkColorScheme else TacticalLightColorScheme
    val semanticPalette = if (darkTheme) DarkRadarSemantics else LightRadarSemantics

    CompositionLocalProvider(LocalRadarColors provides semanticPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

object RadarTheme {
    val colors: RadarSemanticPalette
        @Composable get() = LocalRadarColors.current
}
