package com.dreamboard.app.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Brand palette (dark, default)
val Midnight = Color(0xFF0B1018)
val NavyCharcoal = Color(0xFF182131)
val NavyRaised = Color(0xFF1E293B)
val Hairline = Color(0xFF263247)
val Ivory = Color(0xFFF6F3EB)
val Slate = Color(0xFFA8B3C2)
val Champagne = Color(0xFFC7A878)
val Emerald = Color(0xFF80CBA6)
val Coral = Color(0xFFE9A09B)

// Light counterparts, darkened for WCAG AA contrast on ivory surfaces
val Paper = Color(0xFFF7F4EC)
val PaperRaised = Color(0xFFFFFFFF)
val PaperSunken = Color(0xFFEFEADF)
val PaperHairline = Color(0xFFE2DBCC)
val Ink = Color(0xFF0B1018)
val InkMuted = Color(0xFF55606F)
val GoldDeep = Color(0xFF8C6B38)
val EmeraldDeep = Color(0xFF2F7A57)
val CoralDeep = Color(0xFFB4524B)

/** Restrained, low-saturation categorical palette for allocation and composition charts. */
val ChartPaletteDark = listOf(
    Color(0xFFC7A878), Color(0xFF80CBA6), Color(0xFF8FA7C4), Color(0xFFE3CFA6),
    Color(0xFFA7B8A0), Color(0xFFB79FC4), Color(0xFFE9A09B), Color(0xFF6E7C91),
)
val ChartPaletteLight = listOf(
    Color(0xFF8C6B38), Color(0xFF2F7A57), Color(0xFF4F6A8F), Color(0xFFB08D4F),
    Color(0xFF66805C), Color(0xFF7D5F92), Color(0xFFB4524B), Color(0xFF556274),
)
