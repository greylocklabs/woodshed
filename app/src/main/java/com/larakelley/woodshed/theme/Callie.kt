package com.larakelley.woodshed.theme

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
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
    modifier.fillMaxWidth().heightIn(
      min = 52.dp,
    ).toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(horizontal = horizontalPadding, vertical = 4.dp),
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

/**
 * What a list shows when nothing is in it: a large emoji, a heading, a line of help and, when there is a way out,
 * a button to take it. Centred in the space the list would have used.
 */
@Composable
fun EmptyState(
  emoji: String,
  title: String,
  message: String,
  modifier: Modifier = Modifier,
  action: String? = null,
  onAction: () -> Unit = {},
  /** Set where the empty state fills a fixed area, so it scrolls when space is short; leave off inside a scrolling list. */
  scrolls: Boolean = false,
) {
  Column(
    modifier.fillMaxWidth().then(if (scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier)
      .padding(horizontal = 32.dp, vertical = 40.dp).testTag("empty-state"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(emoji, fontSize = 56.sp)
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(message, style = MaterialTheme.typography.bodyMedium, color = LocalCallie.current.textMuted, textAlign = TextAlign.Center)
    if (action != null) {
      StickerButton(
        onClick = onAction,
        sticker = neutralSticker,
        modifier = Modifier.padding(top = 8.dp).height(44.dp).testTag("empty-action"),
        contentPadding = PaddingValues(horizontal = 18.dp),
      ) {
        Text(action, style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

/**
 * A tool's screen: its controls scroll, and [action], its start or stop button, stays at the bottom in reach,
 * however far down the controls are scrolled.
 */
@Composable
fun PinnedAction(
  action: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  scroll: ScrollState = rememberScrollState(),
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  verticalArrangement: Arrangement.Vertical = Arrangement.Top,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(modifier.fillMaxSize()) {
    Column(
      Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 16.dp).padding(bottom = 16.dp),
      verticalArrangement = verticalArrangement,
      horizontalAlignment = horizontalAlignment,
      content = content,
    )
    HorizontalDivider()
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) { action() }
  }
}
