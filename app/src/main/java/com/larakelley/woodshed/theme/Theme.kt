package com.larakelley.woodshed.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.greylocklabs.ui.GreylockTheme
import org.greylocklabs.ui.Sticker
import org.greylocklabs.ui.Themes

/** The grade buttons: pastel fills at OKLCH lightness 0.86, ink at 0.27 and edge at 0.6, as Callie's calendar colors. */
object Grades {
  val again = Sticker(Color(0xFFFEBFBD), Color(0xFF3F191A), Color(0xFFB26868))
  val hard = Sticker(Color(0xFFEECB9C), Color(0xFF372100), Color(0xFFA17839))
  val easy = Sticker(Color(0xFFA4E1C5), Color(0xFF002F1F), Color(0xFF409272))
}

/** Woodshed in core's Callie theme, or Callie Light when the system is light. */
@Composable
fun WoodshedTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  GreylockTheme(if (darkTheme) Themes.callie else Themes.callieLight, content)
}
