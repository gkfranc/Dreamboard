package com.dreamboard.app.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Brand colours that Material's scheme has no slot for. */
@Immutable
data class DreamboardColors(
    val gold: Color,
    val positive: Color,
    val negative: Color,
    val surfaceRaised: Color,
    val hairline: Color,
    val textMuted: Color,
    val chartPalette: List<Color>,
    val isDark: Boolean,
)

private val DarkBrand = DreamboardColors(
    gold = Champagne, positive = Emerald, negative = Coral, surfaceRaised = NavyRaised,
    hairline = Hairline, textMuted = Slate, chartPalette = ChartPaletteDark, isDark = true,
)
private val LightBrand = DreamboardColors(
    gold = GoldDeep, positive = EmeraldDeep, negative = CoralDeep, surfaceRaised = PaperSunken,
    hairline = PaperHairline, textMuted = InkMuted, chartPalette = ChartPaletteLight, isDark = false,
)

private val DarkScheme = darkColorScheme(
    primary = Champagne, onPrimary = Midnight,
    primaryContainer = Color(0xFF3A3122), onPrimaryContainer = Ivory,
    secondary = Slate, onSecondary = Midnight,
    tertiary = Emerald, onTertiary = Midnight,
    background = Midnight, onBackground = Ivory,
    surface = NavyCharcoal, onSurface = Ivory,
    surfaceVariant = NavyRaised, onSurfaceVariant = Slate,
    surfaceContainer = NavyCharcoal, surfaceContainerHigh = NavyRaised,
    surfaceContainerLow = Color(0xFF121A27), surfaceContainerLowest = Midnight,
    surfaceContainerHighest = Color(0xFF243048),
    outline = Color(0xFF3A475C), outlineVariant = Hairline,
    error = Coral, onError = Midnight,
)

private val LightScheme = lightColorScheme(
    primary = GoldDeep, onPrimary = PaperRaised,
    primaryContainer = Color(0xFFEFE2CB), onPrimaryContainer = Ink,
    secondary = InkMuted, onSecondary = PaperRaised,
    tertiary = EmeraldDeep, onTertiary = PaperRaised,
    background = Paper, onBackground = Ink,
    surface = PaperRaised, onSurface = Ink,
    surfaceVariant = PaperSunken, onSurfaceVariant = InkMuted,
    surfaceContainer = PaperRaised, surfaceContainerHigh = PaperSunken,
    surfaceContainerLow = Paper, surfaceContainerLowest = PaperRaised,
    surfaceContainerHighest = Color(0xFFE8E2D4),
    outline = Color(0xFFB9B1A1), outlineVariant = PaperHairline,
    error = CoralDeep, onError = PaperRaised,
)

val DreamboardShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val LocalDreamboardColors = staticCompositionLocalOf { DarkBrand }

object DbTheme {
    val colors: DreamboardColors
        @Composable get() = LocalDreamboardColors.current
}

@Composable
fun DreamboardTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDreamboardColors provides if (darkTheme) DarkBrand else LightBrand) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = DreamboardTypography,
            shapes = DreamboardShapes,
            content = content,
        )
    }
}
