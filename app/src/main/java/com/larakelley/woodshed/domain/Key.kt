package com.larakelley.woodshed.domain

/** A single tonal center: a pitch class (C = 0) and a mode. */
data class Key(val pitchClass: Int, val minor: Boolean) {
  init {
    require(pitchClass in 0..11) { "pitch class out of range: $pitchClass" }
  }

  fun transpose(semitones: Int): Key = copy(pitchClass = Math.floorMod(pitchClass + semitones, 12))

  /** The key as read on a horn in [transposition], given this key in concert pitch. */
  fun writtenFor(transposition: Transposition): Key = transpose(transposition.offset)

  /** Major keys spell with flats, minor keys with sharps, so names match what musicians write. */
  val displayName: String
    get() = if (minor) "${MINOR_SPELLING[pitchClass]} minor" else MAJOR_SPELLING[pitchClass]

  companion object {
    val MAJOR_SPELLING = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    val MINOR_SPELLING = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#", "A", "Bb", "B")

    /** The order keys get practiced in. */
    val CIRCLE_OF_FOURTHS: List<Int> = List(12) { Math.floorMod(it * 5, 12) }

    private val NATURALS = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)
    private val PATTERN = Regex("^([A-G])([b#]?)(-?)$")

    /**
     * Parses the seed format: a note name, optional `b` or `#`, and a trailing `-` for minor.
     * Returns null for anything else, including compound values like "Eb, C", because a guessed
     * transposition is worse than a visible unknown.
     */
    fun parse(raw: String?): Key? {
      val match = PATTERN.matchEntire(raw ?: return null) ?: return null
      val (letter, accidental, minor) = match.destructured
      val shift = when (accidental) {
        "b" -> -1
        "#" -> 1
        else -> 0
      }
      return Key(Math.floorMod(NATURALS.getValue(letter[0]) + shift, 12), minor == "-")
    }
  }
}
