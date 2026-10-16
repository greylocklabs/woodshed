package com.larakelley.woodshed.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The few icons the app draws, shaped after Material's, so it needs no icon library. */
object AppIcons {
  val ChevronUp: ImageVector = icon("ChevronUp", "M7.41 15.41L12 10.83l4.59 4.58L18 14l-6-6-6 6z")
  val ChevronDown: ImageVector = icon("ChevronDown", "M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z")
  val DragHandle: ImageVector = icon("DragHandle", "M20 9H4v2h16V9zM4 15h16v-2H4v2z")

  private fun icon(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(addPathNodes(path), fill = SolidColor(Color.Black)).build()
}
