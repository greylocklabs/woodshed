package com.larakelley.woodshed.ui.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.larakelley.woodshed.audio.MetronomeState
import com.larakelley.woodshed.domain.Focus
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.PlanBlock
import kotlinx.coroutines.delay

/** What the setup and plan screens can do. */
data class SetupActions(
  val setMinutes: (Int) -> Unit = {},
  val toggleFocus: (Focus) -> Unit = {},
  val toggleTune: (Long) -> Unit = {},
  val buildPlan: () -> Unit = {},
  val discardPlan: () -> Unit = {},
  val swap: (Int) -> Unit = {},
  val remove: (Int) -> Unit = {},
  val start: () -> Unit = {},
)

private val LENGTHS = listOf(15, 30, 45, 60)

/** The Practice tab when no session is running: set one up, then review its plan. */
@Composable
fun PracticeHome(ui: PracticeUi, actions: SetupActions) {
  val plan = ui.setup.plan
  if (plan == null) SessionSetup(ui, actions) else PlanReview(plan, actions)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionSetup(ui: PracticeUi, actions: SetupActions) {
  val setup = ui.setup
  Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
      item {
        ui.summary?.takeIf { it.reviewedToday > 0 }?.let { Text("Reviewed today: ${it.reviewedToday}", modifier = Modifier.padding(bottom = 8.dp)) }
        Text("How long", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          LENGTHS.forEach { m ->
            FilterChip(selected = setup.minutes == m, onClick = { actions.setMinutes(m) }, label = { Text("$m min") }, modifier = Modifier.testTag("length-$m"))
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedButton(onClick = { actions.setMinutes(setup.minutes - 5) }) { Text("-5") }
          Text("${setup.minutes} min", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp).testTag("minutes"))
          OutlinedButton(onClick = { actions.setMinutes(setup.minutes + 5) }) { Text("+5") }
        }
        Spacer(Modifier.height(16.dp))
        Text("Focus", style = MaterialTheme.typography.titleMedium)
        Text("Leave all off for a mix", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Focus.entries.forEach { f ->
            FilterChip(selected = f in setup.focus, onClick = { actions.toggleFocus(f) }, label = { Text(f.label) }, modifier = Modifier.testTag("focus-${f.name.lowercase()}"))
          }
        }
        Spacer(Modifier.height(16.dp))
        Text("Tunes (${setup.selected.size} picked)", style = MaterialTheme.typography.titleMedium)
        if (setup.tunes.isEmpty()) {
          Text("No tunes yet. Add some to your wishlist or start learning them in the Tunes tab.", modifier = Modifier.padding(vertical = 8.dp))
        }
      }
      var lastGroup: TuneGroup? = null
      setup.tunes.forEach { tune ->
        if (tune.group != lastGroup) {
          val group = tune.group
          item(key = "group-$group") {
            Text(group.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
          }
          lastGroup = group
        }
        item(key = tune.id) {
          Row(
            Modifier.fillMaxWidth().clickable { actions.toggleTune(tune.id) }.testTag("pick-${tune.id}"),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(checked = tune.id in setup.selected, onCheckedChange = { actions.toggleTune(tune.id) })
            Text(tune.title)
          }
        }
      }
    }
    Button(onClick = actions.buildPlan, modifier = Modifier.fillMaxWidth().padding(16.dp).height(64.dp).testTag("build-plan")) {
      Text("Plan session", style = MaterialTheme.typography.titleLarge)
    }
  }
}

@Composable
private fun PlanReview(plan: List<PlanItem>, actions: SetupActions) {
  BackHandler(onBack = actions.discardPlan)
  Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = actions.discardPlan) { Text("Back") }
      Text("${plan.sumOf { it.block.minutes }} min planned", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).testTag("plan-total"))
    }
    if (plan.isEmpty()) {
      Text("Nothing fits. Go back and pick tunes, a focus or a longer session.", modifier = Modifier.padding(16.dp))
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
      itemsIndexed(plan) { i, item ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("plan-$i"), verticalAlignment = Alignment.CenterVertically) {
          Text("${item.block.minutes} min", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 12.dp))
          Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(if (item.block is PlanBlock.Tune) "Tune: ${item.detail}" else item.detail, style = MaterialTheme.typography.bodySmall)
          }
          TextButton(onClick = { actions.swap(i) }, modifier = Modifier.testTag("swap-$i")) { Text("Swap") }
          TextButton(onClick = { actions.remove(i) }, modifier = Modifier.testTag("remove-$i")) { Text("Drop") }
        }
        HorizontalDivider()
      }
    }
    if (plan.isNotEmpty()) {
      Button(onClick = actions.start, modifier = Modifier.fillMaxWidth().padding(16.dp).height(72.dp).testTag("start")) {
        Text("Start", style = MaterialTheme.typography.titleLarge)
      }
    }
  }
}

/** A running session: one block at a time, with large controls usable while holding a horn. */
@Composable
fun SessionScreen(
  ui: PracticeUi,
  onGrade: (Grade) -> Unit,
  onNext: () -> Unit,
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
    val step = ui.step
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(if (step == null) "Done" else "${ui.stepIndex + 1} of ${ui.stepCount}", modifier = Modifier.weight(1f).padding(start = 8.dp))
      if (step != null) StepTimer(ui.stepIndex, step.minutes)
      TextButton(onClick = onStop) { Text("End session") }
    }
    if (step == null) {
      Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Session done", style = MaterialTheme.typography.headlineMedium)
        Text("${ui.stepCount} blocks, ${ui.reviewedThisSession} tunes reviewed.", modifier = Modifier.testTag("session-summary"))
        Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(72.dp)) { Text("Finish") }
      }
      return@Column
    }
    Column(
      Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      when (step) {
        is StepView.Exercise -> {
          Text(step.name, style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("step-title"))
          Text(listOfNotNull(step.category, step.tempoBpm?.let { "$it bpm" }).joinToString(", "), style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(8.dp))
          Text(step.description, style = MaterialTheme.typography.titleMedium)
        }
        is StepView.Tune -> {
          val card = step.card
          Text(card.title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("card-title"))
          Text(card.key, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("card-key"))
          if (card.shape.isNotEmpty()) Text(card.shape, style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(8.dp))
          Text(card.exercise, style = MaterialTheme.typography.headlineSmall)
          Text(card.instructions, style = MaterialTheme.typography.titleMedium)
        }
      }
    }
    MetronomeBar(metronome, onToggleMetronome, onSetBpm)
    when (step) {
      is StepView.Exercise ->
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.2f).padding(8.dp).testTag("step-done")) {
          Text("Done", style = MaterialTheme.typography.headlineSmall)
        }
      is StepView.Tune -> {
        TextButton(onClick = onNext, modifier = Modifier.align(Alignment.End).testTag("step-skip")) { Text("Skip without grading") }
        GradeButtons(step.card.buttons, onGrade, Modifier.fillMaxHeight(0.3f))
      }
    }
  }
}

/** Time on the current block against its planned minutes; restarts when the block changes. */
@Composable
private fun StepTimer(stepIndex: Int, minutes: Int) {
  var elapsed by remember(stepIndex) { mutableLongStateOf(0L) }
  LaunchedEffect(stepIndex) {
    while (true) {
      delay(1000)
      elapsed += 1
    }
  }
  val over = elapsed >= minutes * 60L
  Text(
    "%d:%02d of %d:00".format(elapsed / 60, elapsed % 60, minutes),
    color = if (over) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.padding(horizontal = 8.dp).testTag("step-timer"),
  )
}
