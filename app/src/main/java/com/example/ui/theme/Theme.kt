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
    primary = CyanPulse,
    onPrimary = Color(0xFF001F26),
    primaryContainer = CyanPulseContainer,
    onPrimaryContainer = Color(0xFFA6F5FF),
    secondary = EmeraldShield,
    onSecondary = Color(0xFF002116),
    secondaryContainer = EmeraldShieldContainer,
    onSecondaryContainer = Color(0xFF86EFAC),
    tertiary = AmberHandshake,
    onTertiary = Color(0xFF281800),
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = Color(0xFFFDE68A),
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = Color(0xFFFCA5A5),
    background = ObsidianBackground,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF1C273B)
)

private val LightColorScheme = lightColorScheme(
    primary = CyanDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF7FF),
    onPrimaryContainer = Color(0xFF003640),
    secondary = EmeraldDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF022C22),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = SlateLightBackground,
    onBackground = TextPrimaryLight,
    surface = SlateLightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = SlateLightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = SlateLightBorder,
    outlineVariant = Color(0xFFE2E8F0)
)

val CipherGuardShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CipherGuardTheme(
    darkTheme: Boolean = true, // Obsidian Cyber dark-first theme, respects light override if toggled
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme || isSystemInDarkTheme()) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CipherGuardTypography,
        shapes = CipherGuardShapes,
        content = content
    )
}
