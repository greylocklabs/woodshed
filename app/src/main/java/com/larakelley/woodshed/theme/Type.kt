package com.larakelley.woodshed.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.larakelley.woodshed.R

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int) =
  FontFamily(weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) })

/** Nunito for everything you read; Fraunces for display text. Both ship with Callie under the SIL OFL. */
val Nunito = variable(R.font.nunito, 400, 500, 600, 700, 800)
val Fraunces = variable(R.font.fraunces, 400, 600, 700)

private val base = Typography()

val Typography = Typography(
  displayLarge = base.displayLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
  displayMedium = base.displayMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
  displaySmall = base.displaySmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
  headlineLarge = base.headlineLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
  headlineMedium = base.headlineMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
  headlineSmall = base.headlineSmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
  titleLarge = base.titleLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
  titleMedium = base.titleMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
  titleSmall = base.titleSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
  bodyLarge = base.bodyLarge.copy(fontFamily = Nunito),
  bodyMedium = base.bodyMedium.copy(fontFamily = Nunito),
  bodySmall = base.bodySmall.copy(fontFamily = Nunito),
  labelLarge = base.labelLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
  labelMedium = base.labelMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold),
  labelSmall = base.labelSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold),
)
