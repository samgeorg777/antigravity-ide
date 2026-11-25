package com.agyide.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object CosmicColors {
    val Background = Color(0xFF0B0F19)
    val Surface = Color(0xFF111625)
    val SurfaceElevated = Color(0xFF151B28)
    val SurfaceBorder = Color(0xFF1E293B)
    val Cyan = Color(0xFF00F0FF)
    val CyanMuted = Color(0xFF38BDF8)
    val Purple = Color(0xFF7928CA)
    val Violet = Color(0xFFC084FC)
    val Green = Color(0xFF22C55E)
    val Red = Color(0xFFFF5370)
    val Amber = Color(0xFFFFD166)
    val TextPrimary = Color(0xFFE2E8F0)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
}

private val CosmicColorScheme = darkColorScheme(
    primary = CosmicColors.Cyan,
    onPrimary = CosmicColors.Background,
    primaryContainer = Color(0xFF083344),
    onPrimaryContainer = CosmicColors.Cyan,
    secondary = CosmicColors.Violet,
    onSecondary = CosmicColors.Background,
    secondaryContainer = Color(0xFF2E1065),
    onSecondaryContainer = CosmicColors.Violet,
    tertiary = CosmicColors.CyanMuted,
    background = CosmicColors.Background,
    onBackground = CosmicColors.TextPrimary,
    surface = CosmicColors.Surface,
    onSurface = CosmicColors.TextPrimary,
    surfaceVariant = CosmicColors.SurfaceElevated,
    onSurfaceVariant = CosmicColors.TextSecondary,
    outline = CosmicColors.SurfaceBorder,
    error = CosmicColors.Red,
    onError = Color.White
)

private val IdeTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = CosmicColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = CosmicColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = CosmicColors.TextPrimary
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = CosmicColors.TextSecondary
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp
    )
)

@Composable
fun AntigravityIdeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CosmicColorScheme,
        typography = IdeTypography,
        content = content
    )
}
