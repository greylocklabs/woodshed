package com.larakelley.woodshed.ui.tools

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.larakelley.woodshed.audio.MetronomeState
import com.larakelley.woodshed.domain.ClickTrack
import com.larakelley.woodshed.domain.Feel
import com.larakelley.woodshed.domain.TapTempo
import com.larakelley.woodshed.domain.TempoPreset
import com.larakelley.woodshed.theme.CallieChip
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.theme.PRESS_SPRING
import com.larakelley.woodshed.theme.PillChoice
import com.larakelley.woodshed.theme.SectionLabel
import com.larakelley.woodshed.theme.StickerButton
import com.larakelley.woodshed.theme.SwitchRow
import com.larakelley.woodshed.theme.neutralSticker

/** What the metronome tab can change; the metronome itself lives in the app, so it keeps time across tabs. */
data class MetronomeActions(
  val toggle: () -> Unit = {},
  val setBpm: (Int) -> Unit = {},
  val setBeatsPerBar: (Int) -> Unit = {},
  val setAccentFirst: (Boolean) -> Unit = {},
  val setFeel: (Feel) -> Unit = {},
)

private val METERS = listOf(2, 3, 4, 5, 6, 7)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetronomeScreen(state: MetronomeState, actions: MetronomeActions) {
  val callie = LocalCallie.current
  val taps = remember { TapTempo() }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
      RoundSticker("-") { actions.setBpm(state.bpm - 1) }
      Column(Modifier.width(170.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${state.bpm}", style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center, modifier = Modifier.testTag("bpm"))
        Text("bpm, ${TempoPreset.nearest(state.bpm).label.lowercase()}", style = MaterialTheme.typography.bodyMedium, color = callie.textMuted)
      }
      RoundSticker("+") { actions.setBpm(state.bpm + 1) }
    }
    BeatDots(state, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))
    StickerButton(onClick = actions.toggle, modifier = Modifier.fillMaxWidth().height(60.dp).testTag("metronome-toggle")) {
      Text(if (state.running) "◼ Stop" else "▶ Start", style = MaterialTheme.typography.titleLarge)
    }
    Slider(
      value = state.bpm.toFloat(),
      onValueChange = { actions.setBpm(it.toInt()) },
      valueRange = ClickTrack.MIN_BPM.toFloat()..ClickTrack.MAX_BPM.toFloat(),
      colors = SliderDefaults.colors(thumbColor = callie.accent, activeTrackColor = callie.accent, inactiveTrackColor = callie.surfaceAlt),
      modifier = Modifier.padding(top = 16.dp).testTag("bpm-slider"),
    )
    StickerButton(
      onClick = { taps.tap(SystemClock.uptimeMillis())?.let(actions.setBpm) },
      sticker = neutralSticker,
      radius = 80.dp,
      modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).size(150.dp).testTag("tap-tempo"),
    ) {
      Text("👆\nTap tempo", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }

    SectionLabel("Tempos")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      TempoPreset.entries.forEach { p ->
        CallieChip(
          selected = state.bpm == p.bpm,
          onClick = {
            actions.setBpm(p.bpm)
            p.beatsPerBar?.let(actions.setBeatsPerBar)
          },
          label = { Text("${p.label} ${p.bpm}") },
          modifier = Modifier.testTag("preset-${p.name.lowercase()}"),
        )
      }
    }
    SectionLabel("Beats per bar")
    PillChoice(METERS.map { "$it/4" to it }, state.beatsPerBar, actions.setBeatsPerBar, tagPrefix = "meter")
    SectionLabel("Click on")
    PillChoice(Feel.entries.map { it.label to it }, state.feel, actions.setFeel, tagPrefix = "feel")
    SwitchRow("Accent beat 1", state.accentFirst, actions.setAccentFirst, helper = "A higher click on the downbeat", modifier = Modifier.testTag("accent"))
  }
}

/** One dot per beat: the sounding beat swells in pink, beats that stay silent are hollow. */
@Composable
private fun BeatDots(state: MetronomeState, modifier: Modifier = Modifier) {
  val callie = LocalCallie.current
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
    repeat(state.beatsPerBar) { i ->
      val now = state.running && state.beat == i
      val scale by animateFloatAsState(if (now) 1.35f else 1f, PRESS_SPRING, label = "beat")
      val sounds = state.feel.sounds(i)
      val color = when {
        now -> callie.accent
        sounds -> callie.border
        else -> callie.surfaceAlt
      }
      Box(Modifier.size(if (i == 0 && state.accentFirst && state.feel != Feel.BACKBEAT) 18.dp else 14.dp).scale(scale).background(color, CircleShape))
    }
  }
}

@Composable
private fun RoundSticker(label: String, onClick: () -> Unit) {
  StickerButton(onClick = onClick, sticker = neutralSticker, radius = 26.dp, modifier = Modifier.size(52.dp), contentPadding = PaddingValues(0.dp)) {
    Text(label, style = MaterialTheme.typography.headlineSmall)
  }
}
