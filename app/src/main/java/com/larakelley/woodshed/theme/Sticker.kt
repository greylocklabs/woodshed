package com.larakelley.woodshed.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

private val EDGE = 3.dp

/**
 * A button raised on a 3dp edge in a darker shade, as Callie draws everything you can press. It sinks onto
 * its edge while pressed. Defaults to the pink primary action.
 */
@Composable
fun StickerButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  sticker: Sticker? = null,
  radius: Dp = 11.dp,
  contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
  content: @Composable RowScope.() -> Unit,
) {
  val callie = LocalCallie.current
  val fill = sticker?.fill ?: callie.accent
  val ink = sticker?.ink ?: callie.accentText
  val edge = sticker?.edge ?: callie.accentEdge
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  Box(
    modifier.drawBehind {
      val r = radius.toPx()
      drawRoundRect(edge, topLeft = Offset(0f, EDGE.toPx()), size = Size(size.width, size.height - EDGE.toPx()), cornerRadius = CornerRadius(r, r))
    }.padding(bottom = EDGE),
    propagateMinConstraints = true,
  ) {
    Button(
      onClick = onClick,
      modifier = Modifier.offset { IntOffset(0, if (pressed) EDGE.roundToPx() else 0) },
      shape = RoundedCornerShape(radius),
      colors = ButtonDefaults.buttonColors(containerColor = fill, contentColor = ink),
      elevation = null,
      contentPadding = contentPadding,
      interactionSource = interaction,
      content = content,
    )
  }
}

/** The neutral sticker: raised surface, body text, dark edge. */
val neutralSticker: Sticker
  @Composable get() = LocalCallie.current.let { Sticker(it.surfaceAlt, it.text, it.edge) }

/** Callie's back control: a small neutral sticker with a chevron, set apart from the page's own actions. */
@Composable
fun BackButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  StickerButton(onClick = onClick, modifier = modifier.height(40.dp), sticker = neutralSticker, contentPadding = PaddingValues(start = 10.dp, end = 14.dp)) {
    Text("‹", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 6.dp))
    Text(label, style = MaterialTheme.typography.labelLarge)
  }
}
