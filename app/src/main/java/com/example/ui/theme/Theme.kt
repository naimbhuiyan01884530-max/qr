package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = OpticalCyan,
    onPrimary = OpticalCyanDark,
    primaryContainer = OpticalCyanContainer,
    onPrimaryContainer = Color(0xFFB5F7FF),
    secondary = LaserMint,
    onSecondary = Color(0xFF002916),
    secondaryContainer = LaserMintContainer,
    onSecondaryContainer = Color(0xFFA8FCD4),
    tertiary = PrismViolet,
    onTertiary = Color(0xFF1F1147),
    tertiaryContainer = PrismVioletContainer,
    onTertiaryContainer = Color(0xFFE5DBFF),
    background = StudioVoid,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = StudioTextMuted,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHighest,
    outline = StudioOutline,
    outlineVariant = Color(0xFF1E2635),
    error = StudioError,
    onError = Color(0xFF33050F),
    errorContainer = StudioErrorContainer,
    onErrorContainer = Color(0xFFFFD6DD)
)

private val LightColorScheme = lightColorScheme(
    primary = DaylightPrimary,
    onPrimary = Color.White,
    primaryContainer = DaylightPrimaryContainer,
    onPrimaryContainer = Color(0xFF002A33),
    secondary = DaylightSecondary,
    onSecondary = Color.White,
    secondaryContainer = DaylightSecondaryContainer,
    onSecondaryContainer = Color(0xFF00331E),
    tertiary = DaylightTertiary,
    onTertiary = Color.White,
    tertiaryContainer = DaylightTertiaryContainer,
    onTertiaryContainer = Color(0xFF280B5E),
    background = DaylightCanvas,
    onBackground = DaylightTextPrimary,
    surface = DaylightSurface,
    onSurface = DaylightTextPrimary,
    surfaceVariant = DaylightSurfaceElevated,
    onSurfaceVariant = DaylightTextMuted,
    surfaceContainerLow = DaylightSurface,
    surfaceContainer = DaylightSurfaceElevated,
    surfaceContainerHigh = Color(0xFFDDE6F0),
    outline = DaylightOutline,
    outlineVariant = Color(0xFFE2E8F0),
    error = DaylightError,
    onError = Color.White,
    errorContainer = DaylightErrorContainer,
    onErrorContainer = Color(0xFF7F1D1D)
)

val ScanVaultShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ScanVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ScanVaultTypography,
        shapes = ScanVaultShapes,
        content = content
    )
}
