package com.larakelley.woodshed.domain

/**
 * Common jazz tempos, set at the middle of the ranges players and teachers give for each, which overlap and vary
 * by tune. [beatsPerBar] is set for styles that imply a meter.
 */
enum class TempoPreset(val label: String, val bpm: Int, val beatsPerBar: Int? = null) {
  BALLAD("Ballad", 66),
  WALKING_BALLAD("Walking ballad", 84),
  SLOW_SWING("Slow swing", 96),
  BOSSA("Bossa nova", 130),
  MEDIUM_SWING("Medium swing", 132),
  JAZZ_WALTZ("Jazz waltz", 160, beatsPerBar = 3),
  MEDIUM_UP("Medium up", 176),
  BEBOP("Bebop", 220),
  UP_TEMPO("Up tempo", 260),
  BURNER("Burner", 300),
}

/**
 * Tempo from taps: the average of the last few intervals. A pause longer than [resetAfterMs] starts over, so a
 * fresh run of taps is not averaged with an old one.
 */
class TapTempo(private val window: Int = 4, private val resetAfterMs: Long = 2_000) {
  private val taps = ArrayDeque<Long>()

  /** Records a tap at [atMs] and returns the tempo so far, or null until there are two taps. */
  fun tap(atMs: Long): Int? {
    if (taps.isNotEmpty() && atMs - taps.last() > resetAfterMs) taps.clear()
    taps.addLast(atMs)
    while (taps.size > window + 1) taps.removeFirst()
    if (taps.size < 2) return null
    val interval = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
    return Math.round(60_000 / interval).toInt().coerceIn(ClickTrack.MIN_BPM, ClickTrack.MAX_BPM)
  }
}
