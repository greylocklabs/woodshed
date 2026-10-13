package com.larakelley.woodshed.domain

import kotlin.math.log2
import kotlin.math.roundToInt

/** A heard pitch named against the nearest equal-tempered note: [cents] off it, from -50 to +50. */
data class NoteReading(val pitchClass: Int, val octave: Int, val cents: Int, val hz: Double) {
  /** The note as read on a horn in [transposition]; octave numbers follow the written pitch. */
  fun writtenFor(transposition: Transposition): NoteReading {
    val midi = midi + transposition.offset
    return copy(pitchClass = Math.floorMod(midi, 12), octave = Math.floorDiv(midi, 12) - 1)
  }

  private val midi: Int
    get() = (octave + 1) * 12 + pitchClass

  /** Sharps, as tuners show them. */
  val name: String
    get() = SHARP_NAMES[pitchClass]

  companion object {
    val SHARP_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun of(hz: Double, a4: Double = 440.0): NoteReading {
      val semitones = 12 * log2(hz / a4) + 69
      val midi = semitones.roundToInt()
      val cents = ((semitones - midi) * 100).roundToInt()
      return NoteReading(Math.floorMod(midi, 12), Math.floorDiv(midi, 12) - 1, cents, hz)
    }
  }
}

/**
 * The YIN pitch detector (de Cheveigne and Kawahara, 2002): finds the period with the lowest cumulative mean
 * normalised difference, refined by parabolic interpolation. Returns null for silence or noise with no clear pitch.
 */
object Yin {
  private const val THRESHOLD = 0.15

  fun detect(samples: FloatArray, sampleRate: Int, minHz: Double = 40.0, maxHz: Double = 2_000.0): Double? {
    val maxLag = minOf(samples.size / 2, (sampleRate / minHz).toInt())
    val minLag = (sampleRate / maxHz).toInt().coerceAtLeast(2)
    if (maxLag <= minLag) return null
    var energy = 0.0
    for (x in samples) energy += x * x
    if (energy / samples.size < 1e-6) return null

    val d = DoubleArray(maxLag + 1)
    for (lag in 1..maxLag) {
      var sum = 0.0
      for (i in 0 until samples.size - maxLag) {
        val diff = samples[i] - samples[i + lag]
        sum += diff * diff
      }
      d[lag] = sum
    }
    // Cumulative mean normalisation: d'(lag) = d(lag) * lag / sum of d(1..lag).
    val cmnd = DoubleArray(maxLag + 1)
    cmnd[0] = 1.0
    var running = 0.0
    for (lag in 1..maxLag) {
      running += d[lag]
      cmnd[lag] = if (running == 0.0) 1.0 else d[lag] * lag / running
    }
    var lag = minLag
    while (lag <= maxLag) {
      if (cmnd[lag] < THRESHOLD) {
        while (lag + 1 <= maxLag && cmnd[lag + 1] < cmnd[lag]) lag++
        break
      }
      lag++
    }
    if (lag > maxLag) return null
    val better = if (lag in 1 until maxLag) {
      val (a, b, c) = Triple(cmnd[lag - 1], cmnd[lag], cmnd[lag + 1])
      val denom = a - 2 * b + c
      if (denom == 0.0) lag.toDouble() else lag + (a - c) / (2 * denom)
    } else {
      lag.toDouble()
    }
    return sampleRate / better
  }
}
