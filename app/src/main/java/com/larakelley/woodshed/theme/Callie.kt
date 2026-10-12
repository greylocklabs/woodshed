package com.larakelley.woodshed.theme

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A small uppercase heading over a group of settings, as Callie labels its sections. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
  Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.ExtraBold),
    color = LocalCallie.current.textFaint,
    modifier = modifier.padding(top = 16.dp, bottom = 4.dp),
  )
}

/**
 * One-of-several choice drawn as Callie draws tabs and segmented options: the chosen one is a pink sticker,
 * the rest plain text. Scrolls sideways when the options outgrow the width.
 */
@Composable
fun <T> PillChoice(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, tagPrefix: String = "pill") {
  Row(
    modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    options.forEach { (label, value) ->
      val tag = Modifier.testTag("$tagPrefix-${label.lowercase().replace(' ', '-')}")
      if (value == selected) {
        StickerButton(onClick = { onSelect(value) }, modifier = tag.height(44.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
          Text(label, style = MaterialTheme.typography.labelLarge)
        }
      } else {
        val interaction = remember { MutableInteractionSource() }
        TextButton(
          onClick = { onSelect(value) },
          modifier = tag.height(41.dp).pressBounce(interaction),
          shape = RoundedCornerShape(11.dp),
          contentPadding = PaddingValues(horizontal = 16.dp),
          interactionSource = interaction,
        ) {
          Text(label, style = MaterialTheme.typography.labelLarge, color = LocalCallie.current.text)
        }
      }
    }
  }
}

/** Callie's toggle: pink track and dark thumb when on, a quiet raised track when off. */
@Composable
fun CallieSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
  val c = LocalCallie.current
  Switch(
    checked = checked,
    onCheckedChange = onCheckedChange,
    modifier = modifier,
    colors = SwitchDefaults.colors(
      checkedTrackColor = c.accent,
      checkedThumbColor = c.accentText,
      checkedBorderColor = c.accent,
      uncheckedTrackColor = c.surfaceAlt,
      uncheckedThumbColor = c.textFaint,
      uncheckedBorderColor = c.border,
    ),
  )
}

/** A setting's label, optional helper text under it, and its toggle at the far right; the whole row toggles. */
@Composable
fun SwitchRow(
  label: String,
  checked: Boolean,
  onChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  helper: String? = null,
  horizontalPadding: Dp = 0.dp,
) {
  // The row is the touch target, so its pressed highlight spans the full width it is given.
  Row(
    modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(horizontal = horizontalPadding, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f).padding(end = 12.dp)) {
      Text(label, style = MaterialTheme.typography.bodyLarge)
      helper?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalCallie.current.textMuted) }
    }
    CallieSwitch(checked = checked, onCheckedChange = null)
  }
}

/** Muted explanatory text under a setting. */
@Composable
fun Helper(text: String, modifier: Modifier = Modifier) {
  Text(text, style = MaterialTheme.typography.bodySmall, color = LocalCallie.current.textMuted, modifier = modifier)
}
