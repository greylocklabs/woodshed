package com.larakelley.woodshed.domain

/** Whether a tune's home key is major or minor. A tune has one tonality; only its key changes. */
enum class Tonality(val label: String) {
  MAJOR("Major"),
  MINOR("Minor");

  companion object {
    fun fromSeedName(name: String?): Tonality? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
  }
}

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
    private val PATTERN = Regex("^([A-G])([b#]?)$")

    /** The pitch class of a note name such as "Eb" or "F#", or null when it is anything else. */
    fun pitchClassOf(name: String?): Int? {
      val match = PATTERN.matchEntire(name ?: return null) ?: return null
      val (letter, accidental) = match.destructured
      val shift = when (accidental) {
        "b" -> -1
        "#" -> 1
        else -> 0
      }
      return Math.floorMod(NATURALS.getValue(letter[0]) + shift, 12)
    }

    /** A tonic and tonality from the seed, or null when the tonic is unknown or does not parse. */
    fun of(tonic: String?, tonality: Tonality?): Key? = pitchClassOf(tonic)?.let { Key(it, tonality == Tonality.MINOR) }
  }
}
