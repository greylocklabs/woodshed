package com.larakelley.woodshed.ui.media

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.larakelley.woodshed.data.clock
import com.larakelley.woodshed.theme.Helper
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.theme.PillChoice
import com.larakelley.woodshed.theme.StickerButton
import com.larakelley.woodshed.theme.neutralSticker

/** The page an embedded player is served from. YouTube refuses embeds that arrive with no referring page. */
const val PLAYER_ORIGIN = "https://larakelley.com/"

/**
 * Helpers installed in the player page. The app loads the player itself as the page, so these reach its video
 * directly: speed is reapplied whenever the video element is replaced, and a loop jumps back once playback passes B.
 */
private const val HELPERS = """
if (!window.__ws) {
  window.__ws = { rate: 1, loop: null,
    v: function () { return document.querySelector('video'); },
    seek: function (t) { var v = this.v(); if (v) { v.currentTime = Math.max(0, t); v.play(); } },
    nudge: function (s) { var v = this.v(); if (v) { v.currentTime = Math.max(0, v.currentTime + s); } },
    time: function () { var v = this.v(); return v ? v.currentTime : -1; } };
  setInterval(function () {
    var w = window.__ws, v = w.v();
    if (!v) return;
    if (v.playbackRate !== w.rate) v.playbackRate = w.rate;
    if (w.loop && v.currentTime >= w.loop.b) v.currentTime = w.loop.a;
  }, 150);
}
"""

/** Drives a player page's video. Calls before the video exists are kept (speed, loop) or ignored (seeks). */
class PlayerControl(private val web: WebView) {
  private fun run(js: String, result: ((String) -> Unit)? = null) = web.evaluateJavascript(HELPERS + js) { result?.invoke(it) }

  fun setRate(rate: Float) = run("window.__ws.rate = $rate;")

  fun seek(seconds: Int) = run("window.__ws.seek($seconds);")

  fun nudge(seconds: Int) = run("window.__ws.nudge($seconds);")

  fun setLoop(a: Float?, b: Float?) = run(if (a == null || b == null) "window.__ws.loop = null;" else "window.__ws.loop = {a: $a, b: $b};")

  /** Reports where playback is, in seconds, or null before the video has loaded. */
  fun time(onTime: (Float?) -> Unit) = run("window.__ws.time();") { onTime(it.toFloatOrNull()?.takeIf { t -> t >= 0 }) }
}

/**
 * A player with transcribing controls: slower speeds, five seconds back, back to the solo's start, and an A-B loop.
 * [embedUrl] already starts at the padded start; [soloStart] is where "Solo start" jumps.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PracticePlayer(embedUrl: String, soloStart: Int?, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val callie = LocalCallie.current
  val web = remember(embedUrl) {
    WebView(context).apply {
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true
      settings.mediaPlaybackRequiresUserGesture = false
      webChromeClient = WebChromeClient()
      setBackgroundColor(android.graphics.Color.BLACK)
      // The player page fills the view; the Referer header satisfies YouTube's check for an embedding page.
      loadUrl(embedUrl, mapOf("Referer" to PLAYER_ORIGIN))
    }
  }
  DisposableEffect(web) { onDispose { web.destroy() } }
  val control = remember(web) { PlayerControl(web) }
  var rate by remember { mutableFloatStateOf(1f) }
  var loopA by remember { mutableStateOf<Float?>(null) }
  var loopB by remember { mutableStateOf<Float?>(null) }

  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    AndroidView(
      factory = { web },
      modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(callie.surfaceAlt).testTag("player"),
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      SmallSticker("⏪ 5s", "back-5") { control.nudge(-5) }
      if (soloStart != null) SmallSticker("⏮ Solo start", "solo-start") { control.seek(soloStart) }
      SmallSticker("5s ⏩", "forward-5") { control.nudge(5) }
    }
    PillChoice(listOf("½×" to 0.5f, "¾×" to 0.75f, "1×" to 1f), rate, { rate = it; control.setRate(it) }, tagPrefix = "speed")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      SmallSticker(loopA?.let { "A ${clock(it.toInt())}" } ?: "Set A", "loop-a") {
        control.time { t -> loopA = t; if (t != null && loopB != null && loopB!! <= t) loopB = null; control.setLoop(loopA, loopB) }
      }
      SmallSticker(loopB?.let { "B ${clock(it.toInt())}" } ?: "Set B", "loop-b") {
        control.time { t -> if (t != null && (loopA == null || t > loopA!!)) { loopB = t; control.setLoop(loopA, loopB) } }
      }
      if (loopA != null || loopB != null) {
        SmallSticker("Clear loop", "loop-clear") { loopA = null; loopB = null; control.setLoop(null, null) }
      }
    }
    Helper(
      when {
        loopA != null && loopB != null -> "Looping ${clock(loopA!!.toInt())} to ${clock(loopB!!.toInt())}."
        loopA != null -> "Play on, then set B where the phrase ends."
        else -> "Set A at the start of a phrase and B at its end to loop it."
      }
    )
  }
}

@Composable
private fun SmallSticker(label: String, tag: String, onClick: () -> Unit) {
  StickerButton(onClick = onClick, sticker = neutralSticker, modifier = Modifier.height(40.dp).testTag(tag), contentPadding = PaddingValues(horizontal = 12.dp)) {
    Text(label, style = MaterialTheme.typography.labelLarge)
  }
}
