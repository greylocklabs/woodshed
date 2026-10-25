package com.larakelley.woodshed.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.theme.StickerButton

/**
 * Something left sounding, shown along the bottom of screens other than its own so it can always be stopped.
 * Tapping it goes back to it.
 */
@Composable
fun PlayingBar(title: String, detail: String, tag: String, onOpen: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
  val callie = LocalCallie.current
  Column(modifier.fillMaxWidth().background(callie.surface)) {
    HorizontalDivider()
    Row(
      Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp).testTag(tag),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = callie.textMuted, maxLines = 1)
      }
      StickerButton(onClick = onStop, modifier = Modifier.height(40.dp).testTag("$tag-stop"), contentPadding = PaddingValues(horizontal = 16.dp)) {
        Text("◼ Stop", style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}
