package com.dreamboard.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * System fonts only (no downloads, works offline): a refined serif for hero figures and headings,
 * the platform sans for everything else. Figures use tabular numerals so columns align.
 */
private val Display = FontFamily.Serif
private val Text = FontFamily.SansSerif
private const val TABULAR = "tnum"

val DreamboardTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = TABULAR),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp, fontFeatureSettings = TABULAR),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 34.sp, fontFeatureSettings = TABULAR),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Text, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, fontFeatureSettings = TABULAR),
    titleMedium = TextStyle(fontFamily = Text, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp, fontFeatureSettings = TABULAR),
    titleSmall = TextStyle(fontFamily = Text, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, fontFeatureSettings = TABULAR),
    bodyLarge = TextStyle(fontFamily = Text, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Text, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Text, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Text, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = Text, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Text, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp),
)
