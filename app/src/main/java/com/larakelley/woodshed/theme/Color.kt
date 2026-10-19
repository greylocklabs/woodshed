package com.larakelley.woodshed.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** A pastel button: its fill, dark ink of the same hue for text, and a deeper edge it sits on. */
@Immutable
data class Sticker(val fill: Color, val ink: Color, val edge: Color)

/**
 * The Callie palette, from core's themes/callie.toml and callie-light.toml: plum-tinted
 * neutrals, one pink accent, and the edges raised buttons sit on.
 */
@Immutable
data class CallieColors(
  val background: Color,
  val surface: Color,
  val surfaceAlt: Color,
  val hairline: Color,
  val border: Color,
  val text: Color,
  val textMuted: Color,
  val textFaint: Color,
  val accent: Color,
  val accentText: Color,
  val danger: Color,
  val edge: Color,
  val accentEdge: Color,
  /** Grade buttons: pastel fills at OKLCH lightness 0.86, ink at 0.27 and edge at 0.6, as Callie's calendar colors. */
  val again: Sticker = Sticker(Color(0xFFFEBFBD), Color(0xFF3F191A), Color(0xFFB26868)),
  val hard: Sticker = Sticker(Color(0xFFEECB9C), Color(0xFF372100), Color(0xFFA17839)),
  val easy: Sticker = Sticker(Color(0xFFA4E1C5), Color(0xFF002F1F), Color(0xFF409272)),
)

/** Callie, as core's callie.toml sets it. */
val CallieDark = CoreCallie

/** Callie Light from core's callie-light.toml, with a deeper pink accent. */
val CallieLight = CoreCallieLight.copy(
  // Deeper than Callie's pink, so near-white text on it is readable at any size.
  accent = Color(0xFFC2407A),
  accentText = Color(0xFFFFFAFC),
  accentEdge = Color(0xFF8E2C5A),
)
