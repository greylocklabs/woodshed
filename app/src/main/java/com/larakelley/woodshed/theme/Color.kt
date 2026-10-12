package com.larakelley.woodshed.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** A pastel button: its fill, dark ink of the same hue for text, and a deeper edge it sits on. */
@Immutable
data class Sticker(val fill: Color, val ink: Color, val edge: Color)

/**
 * The Callie palette from the Callie calendar app (themes/callie.toml and callie-light.toml): plum-tinted
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

val CallieDark = CallieColors(
  background = Color(0xFF1C1220),
  surface = Color(0xFF261A2A),
  surfaceAlt = Color(0xFF33243A),
  hairline = Color(0xFF2C2031),
  border = Color(0xFF3D2C42),
  text = Color(0xFFF6EEF5),
  textMuted = Color(0xFFC9B7C6),
  textFaint = Color(0xFF9A8797),
  accent = Color(0xFFF08FB8),
  accentText = Color(0xFF2E0F20),
  danger = Color(0xFFE66E6D),
  edge = Color(0xFF140C17),
  accentEdge = Color(0xFFA64B7A),
)

val CallieLight = CallieColors(
  background = Color(0xFFFBF5F8),
  surface = Color(0xFFFFFAFC),
  surfaceAlt = Color(0xFFF3E7EE),
  hairline = Color(0xFFEFE2E9),
  border = Color(0xFFE0CCD7),
  text = Color(0xFF2B1A26),
  textMuted = Color(0xFF6B5562),
  textFaint = Color(0xFF8F7A87),
  accent = Color(0xFFE45F97),
  accentText = Color(0xFF2E0F20),
  danger = Color(0xFFC93C45),
  edge = Color(0xFFDCC8D3),
  accentEdge = Color(0xFFA83A6D),
)
