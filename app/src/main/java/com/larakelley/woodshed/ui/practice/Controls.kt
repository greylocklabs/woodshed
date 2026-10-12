package com.larakelley.woodshed.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.larakelley.woodshed.audio.MetronomeState
import com.larakelley.woodshed.domain.Grade

/** Four full-height grade columns: one imprecise thumb tap from a music stand should land. */
@Composable
fun GradeButtons(buttons: List<Pair<Grade, String>>, onGrade: (Grade) -> Unit, modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  Row(modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    buttons.forEach { (grade, delay) ->
      val (container, content) = when (grade) {
        Grade.AGAIN -> scheme.errorContainer to scheme.onErrorContainer
        Grade.HARD -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        Grade.GOOD -> scheme.primary to scheme.onPrimary
        Grade.EASY -> scheme.secondaryContainer to scheme.onSecondaryContainer
      }
      Button(
        onClick = { onGrade(grade) },
        modifier = Modifier.weight(1f).fillMaxHeight().testTag("grade-${grade.name.lowercase()}"),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
          BasicText(
            grade.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleLarge.copy(color = content, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 26.sp),
          )
          Text(delay, style = MaterialTheme.typography.titleMedium, color = content.copy(alpha = 0.8f), maxLines = 1)
        }
      }
    }
  }
}

/** Start/stop and tempo for the metronome, sized to be hit while holding a horn. */
@Composable
fun MetronomeBar(state: MetronomeState, onToggle: () -> Unit, onSetBpm: (Int) -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier.fillMaxWidth().padding(horizontal = 8.dp).height(56.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    // The toggle takes the leftover width so the tempo buttons never get squeezed by a wide font.
    val toggle = Modifier.weight(1f).fillMaxHeight().testTag("metronome-toggle")
    if (state.running) {
      Button(onClick = onToggle, modifier = toggle, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Stop", maxLines = 1) }
    } else {
      FilledTonalButton(onClick = onToggle, modifier = toggle, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Metronome", maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    TempoButton("-5") { onSetBpm(state.bpm - 5) }
    TempoButton("-1") { onSetBpm(state.bpm - 1) }
    Text(
      "${state.bpm}",
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      maxLines = 1,
      modifier = Modifier.width(52.dp).testTag("metronome-bpm"),
    )
    TempoButton("+1") { onSetBpm(state.bpm + 1) }
    TempoButton("+5") { onSetBpm(state.bpm + 5) }
  }
}

@Composable
private fun TempoButton(label: String, onClick: () -> Unit) {
  OutlinedButton(onClick = onClick, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(48.dp).fillMaxHeight()) {
    Text(label, maxLines = 1, softWrap = false)
  }
}
