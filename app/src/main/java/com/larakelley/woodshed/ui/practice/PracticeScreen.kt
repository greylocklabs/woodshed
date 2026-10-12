package com.larakelley.woodshed.ui.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.larakelley.woodshed.audio.MetronomeState
import com.larakelley.woodshed.domain.Grade

/** The Practice tab when no session is running. */
@Composable
fun PracticeHome(ui: PracticeUi, onStart: () -> Unit, onSetDailyNewLimit: (Int) -> Unit) {
  val summary = ui.summary
  Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    if (summary == null) return@Column
    Text("${summary.due} due now", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("due-count"))
    Text("New tunes today: ${summary.newToday} of ${ui.dailyNewLimit}, ${summary.wishlist} waiting on your wishlist")
    if (summary.reviewedToday > 0) Text("Reviewed today: ${summary.reviewedToday}")
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("New tunes per day", modifier = Modifier.weight(1f))
      OutlinedButton(onClick = { onSetDailyNewLimit(ui.dailyNewLimit - 1) }) { Text("-") }
      Text("${ui.dailyNewLimit}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
      OutlinedButton(onClick = { onSetDailyNewLimit(ui.dailyNewLimit + 1) }) { Text("+") }
    }
    val canIntroduce = summary.wishlist > 0 && summary.newToday < ui.dailyNewLimit
    if (summary.due > 0 || canIntroduce) {
      Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(72.dp)) { Text("Start", style = MaterialTheme.typography.titleLarge) }
    } else {
      Text(
        if (summary.wishlist == 0 && summary.reviewedToday == 0) "Nothing to practice yet. Open a tune in the Tunes tab and add it to your wishlist, or start learning it."
        else "Nothing due. Come back tomorrow."
      )
    }
  }
}

/** A running session: one card at a time, graded with large buttons usable while holding a horn. */
@Composable
fun ReviewScreen(
  ui: PracticeUi,
  onGrade: (Grade) -> Unit,
  onStop: () -> Unit,
  metronome: MetronomeState = MetronomeState(),
  onToggleMetronome: () -> Unit = {},
  onSetBpm: (Int) -> Unit = {},
) {
  val view = LocalView.current
  DisposableEffect(Unit) {
    view.keepScreenOn = true
    onDispose { view.keepScreenOn = false }
  }
  BackHandler(onBack = onStop)

  Column(Modifier.fillMaxSize().safeDrawingPadding()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Text("${ui.reviewedThisSession} done", modifier = Modifier.weight(1f).padding(start = 8.dp))
      TextButton(onClick = onStop) { Text("End session") }
    }
    val card = ui.card
    if (card == null) {
      Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Session done", style = MaterialTheme.typography.headlineMedium)
        Text("${ui.reviewedThisSession} reviewed. Nothing else is due.")
        Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(72.dp)) { Text("Finish") }
      }
      return@Column
    }
    Column(
      Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(card.title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("card-title"))
      Text(card.key, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("card-key"))
      if (card.shape.isNotEmpty()) Text(card.shape, style = MaterialTheme.typography.titleMedium)
      Spacer(Modifier.height(8.dp))
      Text(card.exercise, style = MaterialTheme.typography.headlineSmall)
      Text(card.instructions, style = MaterialTheme.typography.titleMedium)
    }
    MetronomeBar(metronome, onToggleMetronome, onSetBpm)
    GradeButtons(card.buttons, onGrade, Modifier.fillMaxHeight(0.3f))
  }
}
