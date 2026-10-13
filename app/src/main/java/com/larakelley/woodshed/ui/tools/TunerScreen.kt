package com.larakelley.woodshed.ui.tools

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.content.ContextCompat
import com.larakelley.woodshed.audio.TunerState
import com.larakelley.woodshed.domain.NoteReading
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.theme.Helper
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.theme.PRESS_SPRING
import com.larakelley.woodshed.theme.SectionLabel
import com.larakelley.woodshed.theme.StickerButton
import com.larakelley.woodshed.theme.neutralSticker
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class TunerActions(val start: () -> Unit = {}, val stop: () -> Unit = {}, val setA4: (Int) -> Unit = {})

/** Within this many cents counts as in tune. */
const val IN_TUNE_CENTS = 5

/** Names what the microphone hears, written for [transposition], on a needle from 50 cents flat to 50 sharp. */
@Composable
fun TunerScreen(state: TunerState, transposition: Transposition, actions: TunerActions) {
  val callie = LocalCallie.current
  val context = LocalContext.current
  var denied by rememberSaveable { mutableStateOf(false) }
  val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> denied = !granted; if (granted) actions.start() }
  val listen = {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
      denied = false
      actions.start()
    } else {
      askMic.launch(Manifest.permission.RECORD_AUDIO)
    }
  }
  val reading = state.reading
  val written = reading?.writtenFor(transposition)

  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Spacer(Modifier.height(24.dp))
    Text(written?.let(::noteText) ?: buildAnnotatedString { append("–") }, style = MaterialTheme.typography.displayLarge, modifier = Modifier.testTag("note"))
    Text(
      when {
        reading == null -> if (state.listening) "Play a long note" else "Tap listen and play a note"
        transposition == Transposition.C -> "%.1f Hz".format(reading.hz)
        else -> "Concert ${reading.name}, %.1f Hz".format(reading.hz)
      },
      style = MaterialTheme.typography.bodyMedium,
      color = callie.textMuted,
    )
    Gauge(reading?.cents, Modifier.fillMaxWidth().height(170.dp).padding(top = 16.dp))
    Text(
      when {
        reading == null -> " "
        abs(reading.cents) <= IN_TUNE_CENTS -> "In tune ✨"
        reading.cents < 0 -> "${-reading.cents} cents flat"
        else -> "${reading.cents} cents sharp"
      },
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.testTag("tuning"),
    )
    Spacer(Modifier.height(16.dp))
    StickerButton(onClick = { if (state.listening) actions.stop() else listen() }, modifier = Modifier.fillMaxWidth().height(60.dp).testTag("tuner-toggle")) {
      Text(if (state.listening) "◼ Stop" else "🎤 Listen", style = MaterialTheme.typography.titleLarge)
    }
    when {
      denied -> Text(
        "The tuner needs the microphone. Allow it for Woodshed in your phone's settings, then tap Listen.",
        color = callie.danger,
        modifier = Modifier.padding(top = 8.dp).testTag("mic-denied"),
      )
      state.failed -> Text(
        "The microphone isn't available. Another app may be using it.",
        color = callie.danger,
        modifier = Modifier.padding(top = 8.dp).testTag("mic-failed"),
      )
    }
    SectionLabel("Reference pitch", Modifier.align(Alignment.Start))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("A = ${state.a4} Hz", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).testTag("a4"))
      StickerButton(onClick = { actions.setA4(state.a4 - 1) }, sticker = neutralSticker, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) { Text("-") }
      StickerButton(onClick = { actions.setA4(state.a4 + 1) }, sticker = neutralSticker, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) { Text("+") }
    }
    Helper("Notes are named for your instrument's key. Most bands tune to A = 440.", Modifier.align(Alignment.Start))
  }
}

/** The note name with its accidental raised and octave lowered, as tuners print them. */
private fun noteText(n: NoteReading) = buildAnnotatedString {
  append(n.name.take(1))
  if (n.name.length > 1) withStyle(SpanStyle(fontSize = 0.5.em, baselineShift = BaselineShift.Superscript)) { append("♯") }
  withStyle(SpanStyle(fontSize = 0.4.em, baselineShift = BaselineShift(-0.05f))) { append("${n.octave}") }
}

/** A half dial from -50 to +50 cents; the band around zero turns mint when the needle sits in it. */
@Composable
private fun Gauge(cents: Int?, modifier: Modifier = Modifier) {
  val callie = LocalCallie.current
  val angle by animateFloatAsState((cents ?: 0).coerceIn(-50, 50) / 50f * 70f, PRESS_SPRING, label = "needle")
  val inTune = cents != null && abs(cents) <= IN_TUNE_CENTS
  Canvas(modifier.testTag("gauge")) {
    val radius = minOf(size.width / 2, size.height) * 0.9f
    val center = Offset(size.width / 2, size.height * 0.95f)
    val arc = Size(radius * 2, radius * 2)
    val topLeft = Offset(center.x - radius, center.y - radius)
    drawArc(callie.surfaceAlt, startAngle = 200f, sweepAngle = 140f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
    drawArc(
      if (inTune) callie.easy.fill else callie.border,
      startAngle = 270f - 7f, sweepAngle = 14f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(14.dp.toPx(), cap = StrokeCap.Round),
    )
    for (c in listOf(-50, -25, 0, 25, 50)) {
      val a = ((c / 50f * 70f) - 90f) * PI.toFloat() / 180f
      val outer = Offset(center.x + cos(a) * (radius - 12.dp.toPx()), center.y + sin(a) * (radius - 12.dp.toPx()))
      val inner = Offset(center.x + cos(a) * (radius - 24.dp.toPx()), center.y + sin(a) * (radius - 24.dp.toPx()))
      drawLine(callie.textFaint, inner, outer, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
    if (cents != null) {
      val a = (angle - 90f) * PI.toFloat() / 180f
      val tip = Offset(center.x + cos(a) * (radius - 18.dp.toPx()), center.y + sin(a) * (radius - 18.dp.toPx()))
      drawLine(if (inTune) callie.easy.edge else callie.accent, center, tip, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
    }
    drawCircle(callie.text, radius = 7.dp.toPx(), center = center)
  }
}
