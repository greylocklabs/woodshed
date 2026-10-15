package com.larakelley.woodshed.ui.practice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.larakelley.woodshed.domain.Focus
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.Instrument
import com.larakelley.woodshed.domain.PlanBlock
import com.larakelley.woodshed.theme.BackButton
import com.larakelley.woodshed.theme.CallieChip
import com.larakelley.woodshed.theme.EmptyState
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.theme.StickerButton
import com.larakelley.woodshed.theme.neutralSticker
import com.larakelley.woodshed.ui.browse.DropdownChip
import com.larakelley.woodshed.ui.browse.ExerciseFacts
import com.larakelley.woodshed.ui.browse.ExerciseRow
import com.larakelley.woodshed.ui.media.PracticePlayer
import kotlinx.coroutines.delay

/** What the setup and plan screens can do. */
data class SetupActions(
  val setInstrument: (Instrument) -> Unit = {},
  val setMinutes: (Int) -> Unit = {},
  val toggleFocus: (Focus) -> Unit = {},
  val toggleTune: (Long) -> Unit = {},
  val usePlaylist: (Long?) -> Unit = {},
  val pickSolo: (Long?) -> Unit = {},
  val buildPlan: () -> Unit = {},
  val discardPlan: () -> Unit = {},
  /** A random different exercise for a block. */
  val shuffle: (Int) -> Unit = {},
  /** The exercises a block's could be replaced with, for choosing one. */
  val replacements: (Int) -> List<ExerciseRow> = { emptyList() },
  val replace: (Int, Long) -> Unit = { _, _ -> },
  val remove: (Int) -> Unit = {},
  val start: () -> Unit = {},
)

private val LENGTHS = listOf(15, 30, 45, 60)

/** Past this many instruments, chips wrap into a wall; a dropdown keeps the setup short. */
private const val MAX_INSTRUMENT_CHIPS = 5

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
        Text("What are you playing?", style = MaterialTheme.typography.titleMedium)
        Text(
          if (setup.instrumentsChosen) {
            "Sets the key music is written in and keeps exercises to ones for your instrument"
          } else {
            "Sets the key and exercises. Choose the instruments you play in Settings to shorten this list."
          },
          style = MaterialTheme.typography.bodySmall,
        )
        if (setup.instruments.size > MAX_INSTRUMENT_CHIPS) {
          Box(Modifier.padding(vertical = 4.dp)) {
            DropdownChip("Instrument", setup.instrument?.label ?: "Choose", setup.instruments.map { it.label to it }) { i -> i?.let(actions.setInstrument) }
          }
        } else {
          FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            setup.instruments.forEach { i ->
              CallieChip(
                selected = setup.instrument == i,
                onClick = { actions.setInstrument(i) },
                label = { Text(i.label) },
                modifier = Modifier.testTag("instrument-${i.name.lowercase()}"),
              )
            }
          }
        }
        Spacer(Modifier.height(16.dp))
        Text("How long?", style = MaterialTheme.typography.titleMedium)
        FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          LENGTHS.forEach { m ->
            CallieChip(selected = setup.minutes == m, onClick = { actions.setMinutes(m) }, label = { Text("$m min") }, modifier = Modifier.testTag("length-$m"))
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
        FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Focus.entries.forEach { f ->
            CallieChip(selected = f in setup.focus, onClick = {
              actions.toggleFocus(f)
            }, label = { Text("${f.emoji} ${f.label}") }, modifier = Modifier.testTag("focus-${f.name.lowercase()}"))
          }
        }
        if (setup.solos.isNotEmpty()) {
          Spacer(Modifier.height(16.dp))
          Text("Solo to transcribe", style = MaterialTheme.typography.titleMedium)
          Text("Takes a quarter of the session, 5 to 15 minutes. Sessions under 15 minutes leave it out.", style = MaterialTheme.typography.bodySmall)
          FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CallieChip(selected = setup.soloId == null, onClick = {
              actions.pickSolo(null)
            }, label = { Text("None") }, modifier = Modifier.testTag("solo-none"))
            setup.solos.forEach { s ->
              CallieChip(
                selected = setup.soloId == s.id,
                onClick = { actions.pickSolo(s.id) },
                label = { Text("${if (s.active) "🎧" else "💭"} ${s.title} (${s.soloist})") },
                modifier = Modifier.testTag("solo-${s.id}"),
              )
            }
          }
        }
        Spacer(Modifier.height(16.dp))
        Text("Tunes (${setup.selected.size} picked)", style = MaterialTheme.typography.titleMedium)
        if (setup.playlists.isNotEmpty()) {
          DropdownChip(
            "From playlist",
            setup.playlists.firstOrNull { it.first == setup.playlistId }?.second ?: "None",
            setup.playlists.map { it.second to it.first },
            allowAny = true,
            anyLabel = "None",
            onSelect = actions.usePlaylist,
          )
        }
        if (setup.tunes.isEmpty()) {
          EmptyState("🪵", "No tunes yet", "Add tunes to your wishlist or start learning them in the Tunes tab. You can still shed exercises without them.")
        }
      }
      var lastGroup: TuneGroup? = null
      setup.tunes.forEach { tune ->
        if (tune.group != lastGroup) {
          val group = tune.group
          item(key = "group-$group") {
            Text(
              "${group.emoji} ${group.label}",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(top = 12.dp),
            )
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
    if (setup.instrument == null) {
      Text(
        "Pick what you're playing to start",
        color = LocalCallie.current.textMuted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp).testTag("needs-instrument"),
      )
    } else {
      StickerButton(onClick = actions.buildPlan, modifier = Modifier.fillMaxWidth().padding(16.dp).height(64.dp).testTag("build-plan")) {
        Text("🎵 Start shedding", style = MaterialTheme.typography.titleLarge)
      }
    }
  }
}

@Composable
private fun PlanReview(plan: List<PlanItem>, actions: SetupActions) {
  val context = LocalContext.current
  // The session's notification needs this, and one posted before it is granted never appears, so it is asked
  // before the session starts. The session runs whatever the answer.
  val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { actions.start() }
  val start = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
      askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      actions.start()
    }
  }
  BackHandler(onBack = actions.discardPlan)
  var shown by remember { mutableStateOf<ExerciseRow?>(null) }
  var replacing by remember { mutableStateOf<Int?>(null) }
  replacing?.let { index ->
    val options = remember(index, plan) { actions.replacements(index) }
    AlertDialog(
      onDismissRequest = { replacing = null },
      title = { Text("Replace ${plan.getOrNull(index)?.let { if (it.block is PlanBlock.Tune) it.detail else it.title } ?: "exercise"}") },
      text = {
        LazyColumn(Modifier.testTag("replacements")) {
          itemsIndexed(options) { _, option ->
            Column(
              Modifier.fillMaxWidth().clickable {
                actions.replace(index, option.id)
                replacing = null
              }.padding(vertical = 8.dp).testTag("replacement-${option.id}"),
            ) {
              Text(option.name, style = MaterialTheme.typography.titleMedium)
              Text(option.summary, style = MaterialTheme.typography.bodySmall)
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = { replacing = null }) { Text("Cancel") } },
    )
  }
  shown?.let { exercise ->
    AlertDialog(
      onDismissRequest = { shown = null },
      title = { Text(exercise.name) },
      text = { Column(Modifier.verticalScroll(rememberScrollState())) { ExerciseFacts(exercise) } },
      confirmButton = { TextButton(onClick = { shown = null }) { Text("Close") } },
    )
  }
  Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      BackButton("Back", actions.discardPlan, Modifier.padding(end = 12.dp))
      Text("${plan.sumOf { it.block.minutes }} min planned", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).testTag("plan-total"))
    }
    if (plan.isEmpty()) {
      EmptyState("🤷", "Nothing fits", "Go back and pick tunes, a focus or a longer session.", action = "Back to setup", onAction = actions.discardPlan)
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
      itemsIndexed(plan) { i, item ->
        Row(
          Modifier.fillMaxWidth().clickable(enabled = item.exercise != null) {
            shown = item.exercise
          }.padding(horizontal = 16.dp, vertical = 8.dp).testTag("plan-$i"),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("${item.block.minutes} min", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(72.dp))
          Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(if (item.block is PlanBlock.Tune) "Tune: ${item.detail}" else item.detail, style = MaterialTheme.typography.bodySmall)
            Row {
              // A solo block has no exercise to change, only to drop.
              if (item.block !is PlanBlock.Solo) {
                TextButton(onClick = { actions.shuffle(i) }, modifier = Modifier.testTag("shuffle-$i")) { Text("Shuffle") }
                TextButton(onClick = { replacing = i }, modifier = Modifier.testTag("replace-$i")) { Text("Replace") }
              }
              TextButton(onClick = { actions.remove(i) }, modifier = Modifier.testTag("remove-$i")) { Text("Drop") }
            }
          }
        }
        HorizontalDivider()
      }
    }
    if (plan.isNotEmpty()) {
      StickerButton(onClick = start, modifier = Modifier.fillMaxWidth().padding(16.dp).height(72.dp).testTag("start")) {
        Text("🎶 Start", style = MaterialTheme.typography.titleLarge)
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
  /** Sets the metronome to an exercise's tempo and starts it. */
  onUseTempo: (Int) -> Unit = {},
  onPause: () -> Unit = {},
  onResume: () -> Unit = {},
  dronePlaying: Boolean = false,
  /** Starts the drone, on a concert pitch class when given and on its last note otherwise. */
  onDrone: (Int?) -> Unit = {},
  onStopDrone: () -> Unit = {},
  /** Back to the previous block, for a Done tapped too early. */
  onPrevious: () -> Unit = {},
) {
  @Composable
  fun DroneButton(label: String, pitchClass: Int?) {
    StickerButton(
      onClick = { if (dronePlaying) onStopDrone() else onDrone(pitchClass) },
      sticker = neutralSticker,
      modifier = Modifier.height(44.dp).testTag("use-drone"),
    ) { Text(if (dronePlaying) "◼ Stop the drone" else label, style = MaterialTheme.typography.labelLarge) }
  }
  val view = LocalView.current
  val uris = LocalUriHandler.current
  var confirmEnd by rememberSaveable { mutableStateOf(false) }
  DisposableEffect(Unit) {
    view.keepScreenOn = true
    onDispose { view.keepScreenOn = false }
  }
  // Back used to end the session outright; a session now outlives the screen, so ending it is asked first.
  BackHandler { if (ui.step == null) onStop() else confirmEnd = true }
  if (confirmEnd) {
    AlertDialog(
      onDismissRequest = { confirmEnd = false },
      title = { Text("End this session?") },
      text = { Text("Blocks you've finished stay logged.") },
      confirmButton = {
        TextButton(onClick = {
          confirmEnd = false
          onStop()
        }, modifier = Modifier.testTag("confirm-end")) { Text("End session") }
      },
      dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep going") } },
    )
  }

  Column(Modifier.fillMaxSize()) {
    val step = ui.step
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      if (ui.stepIndex > 0) TextButton(onClick = onPrevious, modifier = Modifier.testTag("step-previous")) { Text("‹ Back") }
      Text(if (step == null) "Done" else "${ui.stepIndex + 1} of ${ui.stepCount}", modifier = Modifier.weight(1f).padding(start = 8.dp))
      if (step != null) {
        StepTimer(ui.stepStartedAt, step.minutes, ui.pausedAt)
        TextButton(onClick = if (ui.pausedAt == null) onPause else onResume, modifier = Modifier.testTag("pause")) {
          Text(if (ui.pausedAt == null) "⏸ Pause" else "▶ Resume")
        }
      }
      TextButton(onClick = { if (step == null) onStop() else confirmEnd = true }, modifier = Modifier.testTag("end-session")) { Text("End") }
    }
    if (step == null) {
      Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("🎉 Session done", style = MaterialTheme.typography.headlineMedium)
        Text("${ui.stepCount} blocks, ${ui.reviewedThisSession} tunes reviewed.", modifier = Modifier.testTag("session-summary"))
        StickerButton(onClick = onStop, modifier = Modifier.fillMaxWidth().height(72.dp)) { Text("Finish", style = MaterialTheme.typography.titleLarge) }
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
          Text(step.category, style = MaterialTheme.typography.titleMedium)
          Text(step.description, style = MaterialTheme.typography.titleMedium)
          step.tempoBpm?.let { bpm ->
            StickerButton(onClick = { onUseTempo(bpm) }, sticker = neutralSticker, modifier = Modifier.height(44.dp).testTag("use-tempo")) {
              Text("⏱️ Metronome at $bpm bpm", style = MaterialTheme.typography.labelLarge)
            }
          }
          if (step.description.contains("drone", ignoreCase = true)) DroneButton("🔊 Drone", null)
        }

        is StepView.Solo -> {
          Text("🎧 ${step.title}", style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("solo-title"))
          Text("Transcribe ${step.soloist}", style = MaterialTheme.typography.titleMedium)
          step.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
          if (step.embedUrl != null) {
            PracticePlayer(step.embedUrl, step.soloStart)
          } else {
            OutlinedButton(onClick = { uris.openUri(step.url) }) { Text("Open the recording ↗") }
          }
        }

        is StepView.Tune -> {
          val card = step.card
          Text(card.title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("card-title"))
          Text(card.key, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("card-key"))
          if (card.shape.isNotEmpty()) Text(card.shape, style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(8.dp))
          Text(card.exercise, style = MaterialTheme.typography.headlineSmall)
          Text(card.instructions, style = MaterialTheme.typography.titleMedium)
          card.concertPitchClass?.let { DroneButton("🔊 Drone on the key", it) }
        }
      }
    }
    when (step) {
      is StepView.Exercise ->
        StickerButton(onClick = onNext, modifier = Modifier.fillMaxWidth().padding(16.dp).height(64.dp).testTag("step-done")) {
          Text("✅ Done", style = MaterialTheme.typography.titleLarge)
        }

      is StepView.Tune -> {
        TextButton(onClick = onNext, modifier = Modifier.align(Alignment.End).testTag("step-skip")) { Text("Skip without grading") }
        GradeButtons(step.card.buttons, onGrade, Modifier.fillMaxHeight(0.3f))
      }

      is StepView.Solo -> {
        TextButton(onClick = onNext, modifier = Modifier.align(Alignment.End).testTag("step-skip")) { Text("Skip without grading") }
        GradeButtons(step.buttons, onGrade, Modifier.fillMaxHeight(0.22f))
      }
    }
  }
}

/** Time on the current block against its planned minutes; frozen while [pausedAt] is set. */
@Composable
private fun StepTimer(startedAt: Long, minutes: Int, pausedAt: Long?) {
  // Counts from when the block started rather than from when this screen appeared, so leaving the tab keeps time.
  var elapsed by remember(startedAt) { mutableLongStateOf(0L) }
  LaunchedEffect(startedAt, pausedAt) {
    while (true) {
      elapsed = (((pausedAt ?: System.currentTimeMillis()) - startedAt) / 1000).coerceAtLeast(0)
      if (pausedAt != null) break
      delay(1000)
    }
  }
  val over = elapsed >= minutes * 60L
  Text(
    "%d:%02d of %d:00".format(elapsed / 60, elapsed % 60, minutes),
    color = if (over) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.padding(horizontal = 8.dp).testTag("step-timer"),
  )
}
