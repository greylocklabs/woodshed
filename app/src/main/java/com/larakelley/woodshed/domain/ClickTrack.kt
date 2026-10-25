package com.larakelley.woodshed.domain

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin

/** Which beats of the bar click. */
enum class Feel(val label: String) {
  EVERY_BEAT("Every beat"),

  /** Beats 2 and 4, and every other beat in longer bars, where the hi-hat sits in swing. */
  BACKBEAT("2 and 4"),
  DOWNBEAT("Beat 1 only"),
  ;

  /** Whether the beat at [beatInBar] (0 is beat 1) clicks. */
  fun sounds(beatInBar: Int): Boolean = when (this) {
    EVERY_BEAT -> true
    BACKBEAT -> beatInBar % 2 == 1
    DOWNBEAT -> beatInBar == 0
  }
}

/**
 * Renders metronome clicks into PCM at sample-accurate positions. Each beat's frame is computed from its index,
 * never accumulated, so timing cannot drift however long it runs or however the output is chunked.
 */
class ClickTrack(val sampleRate: Int, val bpm: Int, val beatsPerBar: Int, val accentFirst: Boolean = true, val feel: Feel = Feel.EVERY_BEAT) {
  init {
    require(bpm in MIN_BPM..MAX_BPM) { "bpm out of range: $bpm" }
    require(beatsPerBar >= 1)
  }

  private val framesPerBeat = sampleRate * 60.0 / bpm
  private val clickFrames = (sampleRate * CLICK_SECONDS).toInt()
  private val accent = click(ACCENT_HZ)
  private val normal = click(BEAT_HZ)

  /** The first frame of beat [index]. */
  fun beatFrame(index: Long): Long = (index * framesPerBeat).roundToLong()

  /** Position of beat [index] in its bar, from 0. */
  fun beatInBar(index: Long): Int = Math.floorMod(index, beatsPerBar.toLong()).toInt()

  fun isAccent(index: Long): Boolean = accentFirst && beatInBar(index) == 0 && feel != Feel.BACKBEAT

  fun sounds(index: Long): Boolean = feel.sounds(beatInBar(index))

  /** The click beat [index] sounds, or null for a silent beat; for callers that place clicks themselves. */
  fun clickFor(index: Long): ShortArray? = if (!sounds(index)) {
    null
  } else if (isAccent(index)) {
    accent
  } else {
    normal
  }

  /** Fills [out] with the frames starting at [startFrame]: clicks where beats fall, silence elsewhere. */
  fun render(startFrame: Long, out: ShortArray) {
    out.fill(0)
    val end = startFrame + out.size
    var beat = floor((startFrame - clickFrames) / framesPerBeat).toLong().coerceAtLeast(0)
    while (true) {
      val onset = beatFrame(beat)
      if (onset >= end) break
      if (!sounds(beat)) {
        beat++
        continue
      }
      val wave = if (isAccent(beat)) accent else normal
      for (i in 0 until clickFrames) {
        val frame = onset + i
        if (frame in startFrame until end) out[(frame - startFrame).toInt()] = wave[i]
      }
      beat++
    }
  }

  private fun click(hz: Double): ShortArray = ShortArray(clickFrames) { i ->
    val t = i.toDouble() / sampleRate
    (sin(2 * PI * hz * t) * exp(-t * DECAY) * AMPLITUDE * Short.MAX_VALUE).toInt().toShort()
  }

  companion object {
    const val MIN_BPM = 30
    const val MAX_BPM = 300
    private const val CLICK_SECONDS = 0.03
    private const val ACCENT_HZ = 1760.0
    private const val BEAT_HZ = 1320.0
    private const val DECAY = 120.0
    private const val AMPLITUDE = 0.7

    /** Beats per bar from a time signature like "3/4", defaulting to 4. */
    fun beatsPerBar(timeSignature: String?): Int = timeSignature?.substringBefore('/')?.toIntOrNull()?.takeIf { it in 1..16 } ?: 4
  }
}
