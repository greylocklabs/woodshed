package com.larakelley.woodshed.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.larakelley.woodshed.audio.ChangesSource
import com.larakelley.woodshed.audio.ChangesState
import com.larakelley.woodshed.audio.DroneState
import com.larakelley.woodshed.audio.MetronomeState
import com.larakelley.woodshed.data.MyChanges
import com.larakelley.woodshed.domain.Bar
import com.larakelley.woodshed.domain.ChangesLibrary
import com.larakelley.woodshed.domain.ChangesSection
import com.larakelley.woodshed.domain.ChordGroup
import com.larakelley.woodshed.domain.ChordQuality
import com.larakelley.woodshed.domain.ClickTrack
import com.larakelley.woodshed.domain.DroneRange
import com.larakelley.woodshed.domain.Feel
import com.larakelley.woodshed.domain.Form
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.Move
import com.larakelley.woodshed.domain.Pattern
import com.larakelley.woodshed.domain.PatternUnit
import com.larakelley.woodshed.domain.ProgressionChord
import com.larakelley.woodshed.domain.TempoPreset
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.domain.Voicing
import com.larakelley.woodshed.domain.numerals
import com.larakelley.woodshed.theme.BackButton
import com.larakelley.woodshed.theme.CallieChip
import com.larakelley.woodshed.theme.Helper
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.theme.PillChoice
import com.larakelley.woodshed.theme.SectionLabel
import com.larakelley.woodshed.theme.StickerButton
import com.larakelley.woodshed.theme.SwitchRow
import com.larakelley.woodshed.theme.neutralSticker
import com.larakelley.woodshed.ui.browse.DropdownChip
import kotlin.random.Random

/** What the Changes tab can change; beats per bar, accent and feel are the metronome's, range and voicing the drone's. */
data class ChangesActions(
  val load: (ChangesSource) -> Unit = {},
  val toggle: () -> Unit = {},
  /** Sets the concert key an item, by its key id, plays in. */
  val setKey: (keyId: String, key: Int) -> Unit = { _, _ -> },
  val setClick: (Boolean) -> Unit = {},
  val setCountIn: (Boolean) -> Unit = {},
  val setKeepGoing: (Boolean) -> Unit = {},
  val setBpm: (Int) -> Unit = {},
  val setBeatsPerBar: (Int) -> Unit = {},
  val setAccentFirst: (Boolean) -> Unit = {},
  val setFeel: (Feel) -> Unit = {},
  val setRange: (DroneRange) -> Unit = {},
  val setVoicing: (Voicing) -> Unit = {},
  /** Saves the user's own changes, adding them when the id is null. */
  val save: (id: Long?, name: String, bars: List<Bar>) -> Unit = { _, _, _ -> },
  val delete: (id: Long) -> Unit = {},
)

/** The chords a pattern offers before "More chords" shows the rest. */
private val COMMON_CHORDS =
  listOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.MAJOR7, ChordQuality.DOMINANT7, ChordQuality.MINOR7, ChordQuality.HALF_DIMINISHED)

private fun noteName(pitchClass: Int) = Key(Math.floorMod(pitchClass, 12), minor = false).displayName

/** Where a playing source's screen is, so coming back to the tab while it plays shows it again. */
private fun routeOf(source: String?): String? = when {
  source == null -> null
  source.startsWith("form:") -> source
  source.startsWith("mine:") -> "mine:" + source.removePrefix("mine:").substringBefore(':')
  source.startsWith("pattern:") -> "run:" + source.removePrefix("pattern:").substringBefore(':')
  else -> null
}

/** Progressions to play over: forms, cadences and turnarounds, and patterns that move through the keys. */
@Composable
fun ChangesScreen(
  state: ChangesState,
  metronome: MetronomeState,
  drone: DroneState,
  transposition: Transposition,
  actions: ChangesActions,
  mine: List<MyChanges> = emptyList(),
) {
  // "form:<id>", "pattern:<id>" or "mine:<id>" while one is open, "run:<id>" while a pattern plays, and
  // "edit:<id>" or "edit:new" while writing changes. Back on the tab while changes play, their screen shows.
  var open by rememberSaveable { mutableStateOf(if (state.playing) routeOf(state.source) else null) }
  fun written(keyId: String) = Math.floorMod(state.keyFor(keyId) + transposition.offset, 12)
  val current = open
  when {
    current == null -> ChangesList(state, metronome.beatsPerBar, transposition, actions, mine, ::written) { open = it }

    current.startsWith("mine:") -> {
      val changes = mine.firstOrNull { it.id == current.removePrefix("mine:").toLongOrNull() }
      if (changes == null) {
        open = null
        return
      }
      // Saved again after an edit, the changes load afresh.
      val id = "mine:${changes.id}:${numerals(changes.bars).hashCode()}"
      val keyId = "mine:${changes.id}"
      LaunchedEffect(id) { actions.load(ChangesSource(id, keyId, changes.name) { changes.bars }) }
      val bars = if (state.source == id) state.bars else changes.bars
      ChangesPlayerScreen(
        changes.name,
        keyId,
        "Back to changes",
        state.copy(bars = bars),
        metronome,
        drone,
        transposition,
        actions,
        onEdit = { open = "edit:${changes.id}" },
      ) { open = null }
    }

    current.startsWith("edit:") -> {
      val changes = mine.firstOrNull { it.id == current.removePrefix("edit:").toLongOrNull() }
      ChangesEditor(
        changes,
        written(changes?.let { "mine:${it.id}" } ?: ""),
        onSave = { name, bars ->
          actions.save(changes?.id, name, bars)
          open = changes?.let { "mine:${it.id}" }
        },
        onCancel = { open = changes?.let { "mine:${it.id}" } },
        onDelete = changes?.let {
          {
            actions.delete(it.id)
            open = null
          }
        },
      )
    }

    current.startsWith("form:") -> {
      val form = ChangesLibrary.form(current.removePrefix("form:"))
      if (form == null) {
        open = null
        return
      }
      val id = "form:${form.id}"
      LaunchedEffect(form.id) { actions.load(ChangesSource(id, id, form.name) { form.bars }) }
      // Until the player has it, the form shows from the library.
      val bars = if (state.source == id) state.bars else form.bars
      ChangesPlayerScreen(form.name, id, "Back to changes", state.copy(bars = bars), metronome, drone, transposition, actions) { open = null }
    }

    else -> {
      val preset = ChangesLibrary.pattern(current.substringAfter(':'))
      if (preset == null) {
        open = null
        return
      }
      PatternScreen(preset, current.startsWith("run:"), state, metronome, drone, transposition, actions, onBack = { open = null }) { open = it }
    }
  }
}

@Composable
private fun ChangesList(
  state: ChangesState,
  beatsPerBar: Int,
  transposition: Transposition,
  actions: ChangesActions,
  mine: List<MyChanges>,
  written: (keyId: String) -> Int,
  onOpen: (String) -> Unit,
) {
  Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp).testTag("changes-list")) {
      ChangesSection.entries.forEach { section ->
        SectionLabel(section.label, Modifier.padding(horizontal = 16.dp))
        if (section == ChangesSection.PATTERNS) {
          ChangesLibrary.patterns.forEach { p ->
            ChangesRow(p.name, p.description, noteName(written("pattern:${p.id}")), "changes-${p.id}") { onOpen("pattern:${p.id}") }
          }
        } else {
          ChangesLibrary.forms.filter { it.section == section }.forEach { f ->
            val key = noteName(written("form:${f.id}")) + if (f.minor) "m" else ""
            ChangesRow(f.name, f.description, key, "changes-${f.id}", if (f.advanced) "Advanced" else null) { onOpen("form:${f.id}") }
          }
        }
      }
      SectionLabel("Yours", Modifier.padding(horizontal = 16.dp))
      mine.forEach { c ->
        val bars = "${c.bars.size} ${if (c.bars.size == 1) "bar" else "bars"}"
        ChangesRow(c.name, bars, noteName(written("mine:${c.id}")), "changes-mine-${c.id}", "🪵 Yours") { onOpen("mine:${c.id}") }
      }
      StickerButton(
        onClick = { onOpen("edit:new") },
        sticker = neutralSticker,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp).height(44.dp).testTag("changes-new"),
        contentPadding = PaddingValues(horizontal = 18.dp),
      ) { Text("+ New changes", style = MaterialTheme.typography.labelLarge) }
    }
    if (state.playing) NowPlayingBar(state, beatsPerBar, transposition, onOpen = { routeOf(state.source)?.let(onOpen) }, onStop = actions.toggle)
  }
}

/**
 * What is playing, shown wherever its own screen is not, so changes left playing can always be stopped. Tapping it
 * goes back to them.
 */
@Composable
fun NowPlayingBar(state: ChangesState, beatsPerBar: Int, transposition: Transposition, onOpen: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
  val callie = LocalCallie.current
  val written = Math.floorMod(state.key + transposition.offset, 12)
  val chord = state.bars.getOrNull(state.bar)?.chordOn(state.beat.coerceAtLeast(0), beatsPerBar)?.name(written)
  Column(modifier.fillMaxWidth().background(callie.surface)) {
    HorizontalDivider()
    Row(
      Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp).testTag("now-playing"),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text("🎹 ${state.title}", style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(
          if (chord == null) "Counting in" else "$chord, bar ${state.bar + 1} of ${state.bars.size}",
          style = MaterialTheme.typography.bodySmall,
          color = callie.textMuted,
        )
      }
      StickerButton(onClick = onStop, modifier = Modifier.height(40.dp).testTag("now-playing-stop"), contentPadding = PaddingValues(horizontal = 16.dp)) {
        Text("◼ Stop", style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

@Composable
private fun ChangesRow(title: String, description: String, key: String, tag: String, badge: String? = null, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).testTag(tag),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(description, style = MaterialTheme.typography.bodySmall)
    }
    Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End) {
      Text(key, style = MaterialTheme.typography.titleMedium)
      badge?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
    }
  }
  HorizontalDivider()
}

/** Plays the loaded changes, with the metronome's controls and the drone's sound beneath. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChangesPlayerScreen(
  title: String,
  keyId: String,
  back: String,
  state: ChangesState,
  metronome: MetronomeState,
  drone: DroneState,
  transposition: Transposition,
  actions: ChangesActions,
  onEdit: (() -> Unit)? = null,
  onBack: () -> Unit,
) {
  val callie = LocalCallie.current
  val written = Math.floorMod(state.keyFor(keyId) + transposition.offset, 12)
  val bars = state.bars
  val playingBar = if (state.playing) state.bar else -1
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      BackButton(back, onBack, Modifier.weight(1f, fill = false))
      Spacer(Modifier.weight(1f))
      if (onEdit != null) {
        StickerButton(onClick = onEdit, sticker = neutralSticker, modifier = Modifier.height(40.dp).testTag("changes-edit")) {
          Text("✏️ Edit", style = MaterialTheme.typography.labelLarge)
        }
      }
    }
    Text(title, style = MaterialTheme.typography.headlineMedium)
    KeyLine(keyId, state, transposition, actions, if (state.playing && state.chorus > 0) "Chorus ${state.chorus + 1}" else null)

    val now = bars.getOrNull(playingBar.coerceAtLeast(0))?.chordOn(state.beat.coerceAtLeast(0), metronome.beatsPerBar)
    Text(
      now?.name(written) ?: "",
      style = MaterialTheme.typography.displayLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("changes-chord"),
    )
    Text(
      nextLine(state, bars, metronome.beatsPerBar, written),
      style = MaterialTheme.typography.bodyMedium,
      color = callie.textMuted,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth(),
    )
    BeatDots(
      metronome.copy(running = state.playing, beat = if (state.countInBeat >= 0) state.countInBeat else state.beat),
      Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp),
    )
    BarGrid(bars, playingBar, written)
    StickerButton(onClick = actions.toggle, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(60.dp).testTag("changes-toggle")) {
      Text(if (state.playing) "◼ Stop" else "▶ Start", style = MaterialTheme.typography.titleLarge)
    }

    SectionLabel("Tempo")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
      RoundSticker("-") { actions.setBpm(state.bpm - 1) }
      Column(Modifier.width(170.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${state.bpm}", style = MaterialTheme.typography.displayMedium, modifier = Modifier.testTag("changes-bpm"))
        Text("bpm", style = MaterialTheme.typography.bodyMedium, color = callie.textMuted)
      }
      RoundSticker("+") { actions.setBpm(state.bpm + 1) }
    }
    Slider(
      value = state.bpm.toFloat(),
      onValueChange = { actions.setBpm(it.toInt()) },
      valueRange = ClickTrack.MIN_BPM.toFloat()..ClickTrack.MAX_BPM.toFloat(),
      colors = SliderDefaults.colors(thumbColor = callie.accent, activeTrackColor = callie.accent, inactiveTrackColor = callie.surfaceAlt),
      modifier = Modifier.testTag("changes-bpm-slider"),
    )
    SectionLabel("Common tempos")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      TempoPreset.entries.forEach { p ->
        CallieChip(
          selected = state.bpm == p.bpm,
          onClick = {
            actions.setBpm(p.bpm)
            p.beatsPerBar?.let(actions.setBeatsPerBar)
          },
          label = { Text("${p.label} ${p.bpm}") },
          modifier = Modifier.testTag("changes-preset-${p.name.lowercase()}"),
        )
      }
    }
    SectionLabel("Beats per bar")
    PillChoice(METERS.map { "$it/4" to it }, metronome.beatsPerBar, actions.setBeatsPerBar, tagPrefix = "changes-meter")
    SectionLabel("Click on")
    PillChoice(
      listOf<Pair<String, Feel?>>("Off" to null) + Feel.entries.map { it.label to it },
      if (state.click) metronome.feel else null,
      { feel ->
        actions.setClick(feel != null)
        feel?.let(actions.setFeel)
      },
      tagPrefix = "changes-click",
    )
    SwitchRow("Accent beat 1", metronome.accentFirst, actions.setAccentFirst, helper = "A higher click on the downbeat")
    SwitchRow("Count in", state.countIn, actions.setCountIn, helper = "A bar of clicks before the first chord", modifier = Modifier.testTag("changes-count-in"))
    SwitchRow("Keep going", state.keepGoing, actions.setKeepGoing, helper = "Start again when it ends", modifier = Modifier.testTag("changes-keep-going"))
    SectionLabel("Range")
    PillChoice(DroneRange.entries.map { it.label to it }, drone.range, actions.setRange, tagPrefix = "changes-range")
    SectionLabel("Voicing")
    PillChoice(Voicing.entries.map { it.label to it }, drone.voicing, actions.setVoicing, tagPrefix = "changes-voicing")
  }
}

/** The chord coming next and how soon, or what starts the changes. */
private fun nextLine(state: ChangesState, bars: List<Bar>, beatsPerBar: Int, written: Int): String {
  if (!state.playing) return "${bars.size} bars"
  if (state.countInBeat >= 0) return "Counting in: ${beatsPerBar - state.countInBeat}"
  val bar = bars.getOrNull(state.bar) ?: return ""
  val now = bar.chordOn(state.beat, beatsPerBar)
  // Look ahead beat by beat, through the end of the form and round, for the next different chord.
  for (ahead in 1..bars.size * beatsPerBar) {
    val beat = state.beat + ahead
    val next = bars[(state.bar + beat / beatsPerBar) % bars.size].chordOn(beat % beatsPerBar, beatsPerBar)
    if (next != now) return "Next: ${next.name(written)}, in $ahead ${if (ahead == 1) "beat" else "beats"}"
  }
  return ""
}

/** The bars four to a row, each with its chords; the bar being played is tinted as a selected chip is. */
@Composable
private fun BarGrid(bars: List<Bar>, playing: Int, written: Int) {
  val callie = LocalCallie.current
  val shape = RoundedCornerShape(6.dp)
  Column(Modifier.testTag("changes-bars"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    bars.chunked(4).forEachIndexed { row, four ->
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        four.forEachIndexed { i, bar ->
          val index = row * 4 + i
          val now = index == playing
          Box(
            Modifier.weight(1f).height(36.dp).then(
              if (now) Modifier.background(MaterialTheme.colorScheme.secondaryContainer, shape) else Modifier.border(1.dp, callie.hairline, shape),
            ),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              bar.chords.joinToString(" ") { it.name(written) },
              style = MaterialTheme.typography.labelLarge,
              color = if (playing >= 0 && index < playing) callie.textFaint else callie.text,
              maxLines = 1,
            )
          }
        }
        repeat(4 - four.size) { Spacer(Modifier.weight(1f)) }
      }
    }
  }
}

/** A pattern's setup, and once started, its player. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatternScreen(
  preset: Pattern,
  running: Boolean,
  state: ChangesState,
  metronome: MetronomeState,
  drone: DroneState,
  transposition: Transposition,
  actions: ChangesActions,
  onBack: () -> Unit,
  onOpen: (String) -> Unit,
) {
  var move by rememberSaveable(preset.id) { mutableStateOf(preset.move) }
  var unit by rememberSaveable(preset.id) { mutableStateOf(preset.unit) }
  var chord by rememberSaveable(preset.id) { mutableStateOf(preset.chord) }
  var barsPerKey by rememberSaveable(preset.id) { mutableStateOf(preset.barsPerKey) }
  var formId by rememberSaveable(preset.id) { mutableStateOf(preset.form?.id ?: ChangesLibrary.forms.first().id) }
  var allChords by rememberSaveable(preset.id) { mutableStateOf(false) }
  val pattern = preset.copy(move = move, unit = unit, chord = chord, barsPerKey = barsPerKey, form = ChangesLibrary.form(formId))
  val keyId = "pattern:${preset.id}"
  val written = Math.floorMod(state.keyFor(keyId) + transposition.offset, 12)
  if (running) {
    // Reopened after the app was away, the pattern loads again, in a fresh order if it is random.
    if (state.source?.startsWith("pattern:${preset.id}:") != true) LaunchedEffect(preset.id) { actions.load(source(pattern)) }
    ChangesPlayerScreen(preset.name, keyId, "Back to the pattern", state, metronome, drone, transposition, actions) { onOpen("pattern:${preset.id}") }
    return
  }
  val keys = move.keys()
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      BackButton("Back to changes", onBack, Modifier.weight(1f, fill = false))
      Spacer(Modifier.weight(1f))
      // The pattern as it stands, one time through, becomes changes of one's own to edit further.
      StickerButton(onClick = {
        actions.save(null, preset.name, pattern.bars())
        onBack()
      }, sticker = neutralSticker, modifier = Modifier.height(40.dp).testTag("pattern-save")) {
        Text("🪵 Save as yours", style = MaterialTheme.typography.labelLarge)
      }
    }
    Text(preset.name, style = MaterialTheme.typography.headlineMedium)
    KeyLine(keyId, state, transposition, actions, "${pattern.bars().size} bars" + if (state.keepGoing) ", then again" else "")
    SectionLabel("Move")
    PillChoice(Move.entries.map { it.label to it }, move, { move = it }, tagPrefix = "pattern-move")
    SectionLabel("In each key")
    PillChoice(PatternUnit.entries.map { it.label to it }, unit, {
      unit = it
      barsPerKey = it.barsNear(barsPerKey)
    }, tagPrefix = "pattern-unit")
    when (unit) {
      PatternUnit.ONE_CHORD -> {
        SectionLabel("Chord")
        val shown = if (allChords) ChordQuality.entries.filter { it.group != ChordGroup.SIMPLE } else COMMON_CHORDS
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          shown.forEach { q ->
            CallieChip(selected = q == chord, onClick = {
              chord = q
            }, label = { Text(q.label) }, modifier = Modifier.testTag("pattern-chord-${q.name.lowercase()}"))
          }
          if (!allChords) CallieChip(selected = false, onClick = { allChords = true }, label = { Text("More chords") })
        }
      }

      PatternUnit.FORM -> {
        Helper("The whole form plays in each key before moving on.")
        DropdownChip("Form", pattern.form?.name ?: "", ChangesLibrary.forms.map { it.name to it.id }) { it?.let { id -> formId = id } }
      }

      else -> {}
    }
    if (unit != PatternUnit.FORM) {
      SectionLabel("Bars in each key")
      PillChoice(unit.barChoices.map { "$it" to it }, barsPerKey, { barsPerKey = it }, tagPrefix = "pattern-bars")
    }
    SwitchRow("Keep going", state.keepGoing, actions.setKeepGoing, helper = "Start around again after all the keys")
    SectionLabel("The keys, in order")
    BarGrid(keys.map { Bar(listOf(ProgressionChord(it, if (unit == PatternUnit.ONE_CHORD) chord else ChordQuality.MAJOR))) }, -1, written)
    StickerButton(onClick = {
      actions.load(source(pattern))
      actions.toggle()
      onOpen("run:${preset.id}")
    }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(60.dp).testTag("pattern-start")) {
      Text("▶ Start", style = MaterialTheme.typography.titleLarge)
    }
    Helper("Tempo, click and range are set while it plays.", Modifier.align(Alignment.CenterHorizontally))
  }
}

/** What plays [pattern]; a random one takes a fresh order each time it is started. */
private fun source(pattern: Pattern): ChangesSource {
  val seed = Random.nextInt()
  val id = listOf(pattern.id, pattern.move, pattern.unit, pattern.chord, pattern.barsPerKey, pattern.form?.id, seed).joinToString(":")
  return ChangesSource("pattern:$id", "pattern:${pattern.id}", pattern.name) { pattern.bars(seed + it) }
}

/** The key [keyId] plays in, to change it there, with the concert key beside it and [extra] after. */
@Composable
private fun KeyLine(keyId: String, state: ChangesState, transposition: Transposition, actions: ChangesActions, extra: String?) {
  val concert = state.keyFor(keyId)
  val written = Math.floorMod(concert + transposition.offset, 12)
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    DropdownChip("Key", noteName(written), (0..11).map { noteName(it) to it }) { it?.let { w -> actions.setKey(keyId, w - transposition.offset) } }
    val parts = listOfNotNull(if (transposition == Transposition.C) null else "Concert ${noteName(concert)}", extra)
    if (parts.isNotEmpty()) Text(parts.joinToString(". "), style = MaterialTheme.typography.bodyMedium, color = LocalCallie.current.textMuted)
  }
}
