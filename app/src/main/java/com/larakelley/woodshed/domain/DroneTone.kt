package com.larakelley.woodshed.domain

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** The frequency of MIDI note [midi], tuned so A4 (69) is the [a4] reference. */
fun midiHz(midi: Int, a4: Double = 440.0): Double = a4 * 2.0.pow((midi - 69) / 12.0)

/**
 * Where the drone sits. Each range is one octave of concert pitches, from F up to the E above, so every root
 * lands at the same height whatever its name or the instrument's key.
 */
enum class DroneRange(val label: String, val lowestMidi: Int) {
  LOW("Low", 41),
  MIDDLE("Middle", 53),
  HIGH("High", 65),
  ;

  /** The MIDI note of [pitchClass] (C = 0) within this range. */
  fun rootMidi(pitchClass: Int): Int = lowestMidi + Math.floorMod(pitchClass - lowestMidi, 12)

  fun rootHz(pitchClass: Int, a4: Double = 440.0): Double = midiHz(rootMidi(pitchClass), a4)
}

/**
 * A sustained tone to play long tones and intervals against: a root with a few soft overtones, and any other
 * notes of a chord above it. Phases run on across calls, and loudness eases toward its target over each buffer,
 * so changing pitch, the chord or the volume while it plays does not click.
 */
class DroneTone(private val sampleRate: Int) {
  private val phases = Array(MAX_NOTES) { DoubleArray(PARTIALS.size) }
  private val semitones = IntArray(MAX_NOTES)
  private val levels = DoubleArray(MAX_NOTES)
  private var gain = 0.0

  /**
   * Fills [out] with the next samples at [hz], with [notes] (semitones above the root, the root's 0 first) sounding
   * together, fading toward [volume] (0 to 1). Returns the gain reached, so a caller fading out knows when it is silent.
   */
  fun render(out: ShortArray, hz: Double, volume: Double, notes: List<Int> = listOf(0)): Double {
    require(notes.size in 1..MAX_NOTES) { "a drone sounds 1 to $MAX_NOTES notes, not ${notes.size}" }
    // The notes above the root share a fixed budget, so a chord is no louder than a root and fifth.
    val upper = if (notes.size > 1) minOf(UPPER_LEVEL, (MAX_SUM - 1.0) / (notes.size - 1)) else 0.0
    val targets = DoubleArray(MAX_NOTES) { i ->
      if (i == 0) {
        1.0
      } else if (i < notes.size) {
        upper
      } else {
        0.0
      }
    }
    val targetGain = volume.coerceIn(0.0, 1.0)
    val gainStep = (targetGain - gain) / out.size
    val steps = DoubleArray(MAX_NOTES) { (targets[it] - levels[it]) / out.size }
    // Notes move to their new pitches with their phases running on, as the root does; notes the chord drops keep
    // theirs while they fade out.
    for (i in notes.indices) semitones[i] = notes[i]
    val ratios = DoubleArray(MAX_NOTES) { 2.0.pow(semitones[it] / 12.0) }
    for (i in out.indices) {
      gain += gainStep
      var sample = 0.0
      for (n in 0 until MAX_NOTES) {
        levels[n] += steps[n]
        if (levels[n] < 1e-6 && steps[n] <= 0.0) continue
        val noteHz = hz * ratios[n]
        val phase = phases[n]
        for (p in PARTIALS.indices) {
          val (multiple, level) = PARTIALS[p]
          sample += levels[n] * level * sin(phase[p])
          phase[p] = advance(phase[p], noteHz * multiple)
        }
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

    /** The most notes a chord sounds, as a 13th or an altered dominant does. */
    const val MAX_NOTES = 6

    /** A single note above the root, such as the fifth, is this loud against it. */
    const val UPPER_LEVEL = 0.6

    /** The root and every other note together are never louder than a root and fifth. */
    const val MAX_SUM = 1.0 + UPPER_LEVEL

    /** The loudest root-and-fifth sum stays under full scale. */
    const val HEADROOM = 0.32
  }
}
