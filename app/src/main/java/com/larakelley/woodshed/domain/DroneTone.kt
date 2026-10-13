package com.larakelley.woodshed.domain

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** The frequency of [pitchClass] (C = 0) in [octave], numbered so A4 is the [a4] reference. */
fun pitchHz(pitchClass: Int, octave: Int, a4: Double = 440.0): Double {
  val midi = 12 * (octave + 1) + pitchClass
  return a4 * 2.0.pow((midi - 69) / 12.0)
}

/**
 * A sustained tone to play long tones and intervals against: a root with a few soft overtones, optionally with
 * the fifth above. Phases run on across calls, and loudness eases toward its target over each buffer, so changing
 * pitch, the fifth or the volume while it plays does not click.
 */
class DroneTone(private val sampleRate: Int) {
  private val phases = DoubleArray(PARTIALS.size * 2)
  private var gain = 0.0
  private var fifthGain = 0.0

  /**
   * Fills [out] with the next samples at [hz], fading toward [volume] (0 to 1) and toward having the fifth or not.
   * Returns the gain reached, so a caller fading out knows when it is silent.
   */
  fun render(out: ShortArray, hz: Double, volume: Double, fifth: Boolean): Double {
    val targetGain = volume.coerceIn(0.0, 1.0)
    val targetFifth = if (fifth) FIFTH_LEVEL else 0.0
    val gainStep = (targetGain - gain) / out.size
    val fifthStep = (targetFifth - fifthGain) / out.size
    for (i in out.indices) {
      gain += gainStep
      fifthGain += fifthStep
      var sample = 0.0
      for (p in PARTIALS.indices) {
        val (multiple, level) = PARTIALS[p]
        sample += level * sin(phases[p])
        phases[p] = advance(phases[p], hz * multiple)
        val q = PARTIALS.size + p
        sample += fifthGain * level * sin(phases[q])
        phases[q] = advance(phases[q], hz * 1.5 * multiple)
      }
      out[i] = (sample * gain * HEADROOM * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
    return gain
  }

  private fun advance(phase: Double, hz: Double): Double {
    val next = phase + 2 * PI * hz / sampleRate
    return if (next > 2 * PI) next - 2 * PI else next
  }

  private companion object {
    /** Overtone multiples and levels: a mellow, organ-like tone that sits under a horn without masking it. */
    val PARTIALS = listOf(1.0 to 1.0, 2.0 to 0.45, 3.0 to 0.2, 4.0 to 0.08)
    const val FIFTH_LEVEL = 0.6

    /** The loudest root-and-fifth sum stays under full scale. */
    const val HEADROOM = 0.32
  }
}
