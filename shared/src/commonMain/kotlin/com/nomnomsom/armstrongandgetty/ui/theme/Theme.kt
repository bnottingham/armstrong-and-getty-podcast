package com.nomnomsom.armstrongandgetty.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Gold = Color(0xFFE8B34B)
val GoldDark = Color(0xFFD4952A)
val DarkBg = Color(0xFF0E0F13)
val CardBg = Color(0xFF181A21)
val SurfaceVariant = Color(0xFF1C1E26)
val TextPrimary = Color(0xFFF0EDE6)
val TextSecondary = Color(0xFF9B978E)
val TextMuted = Color(0xFF5A5750)
val ErrorRed = Color(0xFFF87171)
val SuccessGreen = Color(0xFF4ADE80)
val LiveRed = Color(0xFFF87171)

private val DarkColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = DarkBg,
    primaryContainer = Gold.copy(alpha = 0.15f),
    secondary = GoldDark,
    background = DarkBg,
    surface = CardBg,
    surfaceVariant = SurfaceVariant,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color.White,
    outline = Color.White.copy(alpha = 0.06f)
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = (-0.3).sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = TextSecondary
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        color = TextSecondary
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = Gold
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        color = TextSecondary
    )
)

@Composable
fun AGPodcastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
