package com.larakelley.woodshed.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.larakelley.woodshed.audio.DroneState
import com.larakelley.woodshed.domain.ChordGroup
import com.larakelley.woodshed.domain.ChordQuality
import com.larakelley.woodshed.domain.DroneRange
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.domain.Voicing
import com.larakelley.woodshed.domain.pianoNotes
import com.larakelley.woodshed.domain.spell
import com.larakelley.woodshed.theme.CallieChip
import com.larakelley.woodshed.theme.Helper
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.theme.PillChoice
import com.larakelley.woodshed.theme.PinnedAction
import com.larakelley.woodshed.theme.SectionLabel
import com.larakelley.woodshed.theme.StickerButton

data class DroneActions(
  val toggle: () -> Unit = {},
  /** Takes a concert pitch class. */
  val setPitchClass: (Int) -> Unit = {},
  val setRange: (DroneRange) -> Unit = {},
  val setChord: (ChordQuality) -> Unit = {},
  val setVoicing: (Voicing) -> Unit = {},
  val setVolume: (Float) -> Unit = {},
)

/** A note name as major keys spell it, which is how drones are usually named. */
private fun noteName(pitchClass: Int) = Key(pitchClass, minor = false).displayName

/** A sustained note or chord to play against, named for [transposition] and sounding in concert pitch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DroneScreen(state: DroneState, transposition: Transposition, a4: Int, actions: DroneActions) {
  val callie = LocalCallie.current
  val written = Math.floorMod(state.pitchClass + transposition.offset, 12)
  PinnedAction(action = {
    StickerButton(onClick = actions.toggle, modifier = Modifier.fillMaxWidth().height(60.dp).testTag("drone-toggle")) {
      Text(if (state.playing) "◼ Stop" else "▶ Start drone", style = MaterialTheme.typography.titleLarge)
    }
  }) {
    Spacer(Modifier.height(24.dp))
    val name = noteName(written)
    Text(
      name + state.chord.symbol,
      style = MaterialTheme.typography.displayLarge,
      modifier = Modifier.align(Alignment.CenterHorizontally).testTag("drone-note"),
    )
    Text(
      (if (state.chord == ChordQuality.NOTE) "" else spell(name, state.chord).joinToString(" ") + ". ") +
        (if (transposition == Transposition.C) "" else "Concert ${noteName(state.pitchClass)}${state.chord.symbol}, ") +
        "%.1f Hz".format(state.range.rootHz(state.pitchClass, a4.toDouble())),
      style = MaterialTheme.typography.bodyMedium,
      color = callie.textMuted,
      modifier = Modifier.align(Alignment.CenterHorizontally),
    )

    SectionLabel("Note")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      (0..11).forEach { w ->
        CallieChip(
          selected = w == written,
          onClick = { actions.setPitchClass(w - transposition.offset) },
          label = { Text(noteName(w)) },
          modifier = Modifier.testTag("drone-note-$w"),
        )
      }
    }
    SectionLabel("Chord")
    ChordChips(state.chord, actions.setChord, "drone-chord")
    SectionLabel("Range")
    PillChoice(DroneRange.entries.map { it.label to it }, state.range, actions.setRange, tagPrefix = "drone-range")
    if (state.chord.voiced) {
      SectionLabel("Voicing")
      PillChoice(Voicing.entries.map { it.label to it }, state.voicing, actions.setVoicing, tagPrefix = "drone-voicing")
      VoicingNotes(state.chord, state.pitchClass, state.range)
    }
    SectionLabel("Volume")
    Slider(
      value = state.volume,
      onValueChange = actions.setVolume,
      colors = SliderDefaults.colors(thumbColor = callie.accent, activeTrackColor = callie.accent, inactiveTrackColor = callie.surfaceAlt),
      modifier = Modifier.testTag("drone-volume"),
    )
    Helper(
      "Notes are named for your instrument's key. The pitch follows the tuner's reference, A = $a4 Hz. Sounds best through headphones or speakers.",
      Modifier.padding(top = 8.dp),
    )
  }
}

/** Every chord quality in [groups], under each group's name, the [selected] one picked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChordChips(selected: ChordQuality?, onSelect: (ChordQuality) -> Unit, tagPrefix: String, groups: List<ChordGroup> = ChordGroup.entries) {
  groups.forEach { group ->
    group.label?.let { Helper(it, Modifier.padding(top = 8.dp)) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      ChordQuality.entries.filter { it.group == group }.forEach { chord ->
        CallieChip(
          selected = chord == selected,
          onClick = { onSelect(chord) },
          label = { Text(chord.label) },
          modifier = Modifier.testTag("$tagPrefix-${chord.name.lowercase()}"),
        )
      }
    }
  }
}

/** Each voicing's piano notes for [quality] on the concert [pitchClass], so the choices can be told apart. */
@Composable
internal fun VoicingNotes(quality: ChordQuality, pitchClass: Int, range: DroneRange) {
  val root = range.rootMidi(pitchClass)
  Helper(
    "On a piano:\n" + Voicing.entries.joinToString("\n") { "${it.label}: ${pianoNotes(root, quality, it).joinToString(" ")}" },
    Modifier.testTag("voicing-notes"),
  )
}
