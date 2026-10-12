package com.larakelley.woodshed.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

val LocalCallie = staticCompositionLocalOf { CallieDark }

/** Callie's corner radii: 6 for small things, 11 for buttons, 14 for cards, 16 for popovers. */
val CallieShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),
  small = RoundedCornerShape(6.dp),
  medium = RoundedCornerShape(11.dp),
  large = RoundedCornerShape(14.dp),
  extraLarge = RoundedCornerShape(16.dp),
)

private fun scheme(c: CallieColors, dark: Boolean): ColorScheme {
  // Selected chips and tonal buttons get a pink wash over the raised surface, so pink stays the one accent.
  val wash = lerp(c.surfaceAlt, c.accent, if (dark) 0.28f else 0.22f)
  val base = if (dark) darkColorScheme() else lightColorScheme()
  return base.copy(
    primary = c.accent,
    onPrimary = c.accentText,
    primaryContainer = wash,
    onPrimaryContainer = c.text,
    secondary = c.accent,
    onSecondary = c.accentText,
    secondaryContainer = wash,
    onSecondaryContainer = c.text,
    tertiary = c.accent,
    onTertiary = c.accentText,
    background = c.background,
    onBackground = c.text,
    surface = c.background,
    onSurface = c.text,
    surfaceVariant = c.surfaceAlt,
    onSurfaceVariant = c.textMuted,
    surfaceContainerLowest = c.background,
    surfaceContainerLow = c.surface,
    surfaceContainer = c.surface,
    surfaceContainerHigh = c.surfaceAlt,
    surfaceContainerHighest = c.surfaceAlt,
    surfaceBright = c.surfaceAlt,
    surfaceDim = c.background,
    outline = c.border,
    outlineVariant = c.hairline,
    error = c.danger,
    onError = c.accentText,
  )
}

@Composable
fun WoodshedTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  val colors = if (darkTheme) CallieDark else CallieLight
  CompositionLocalProvider(LocalCallie provides colors) {
    MaterialTheme(colorScheme = scheme(colors, darkTheme), typography = Typography, shapes = CallieShapes, content = content)
  }
}
