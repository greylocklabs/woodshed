package com.larakelley.woodshed.domain

/** One note of a chord: semitones above the root, and the scale degree it is spelled as (1 for the root, 9 for the ninth). */
data class ChordTone(val semitones: Int, val degree: Int)

/** How the drone's chord pickers group chords. */
enum class ChordGroup(val label: String?) {
  SIMPLE(null),
  TRIAD("Triads"),
  SEVENTH("Sevenths"),
  EXTENDED("Sixths and extensions"),
}

/** What sounds over the root: the note alone, a fifth, or a chord. [symbol] follows the root in a chord name, as in "Bb7". */
enum class ChordQuality(val symbol: String, val label: String, val group: ChordGroup, tones: String) {
  NOTE("", "Just the note", ChordGroup.SIMPLE, "0:1"),
  FIFTH("5", "Note and fifth", ChordGroup.SIMPLE, "0:1 7:5"),
  MAJOR("", "Major", ChordGroup.TRIAD, "0:1 4:3 7:5"),
  MINOR("m", "Minor", ChordGroup.TRIAD, "0:1 3:3 7:5"),
  DIMINISHED("dim", "dim", ChordGroup.TRIAD, "0:1 3:3 6:5"),
  AUGMENTED("aug", "aug", ChordGroup.TRIAD, "0:1 4:3 8:5"),
  SUS2("sus2", "sus2", ChordGroup.TRIAD, "0:1 2:2 7:5"),
  SUS4("sus4", "sus4", ChordGroup.TRIAD, "0:1 5:4 7:5"),
  MAJOR7("maj7", "maj7", ChordGroup.SEVENTH, "0:1 4:3 7:5 11:7"),
  DOMINANT7("7", "7", ChordGroup.SEVENTH, "0:1 4:3 7:5 10:7"),
  MINOR7("m7", "m7", ChordGroup.SEVENTH, "0:1 3:3 7:5 10:7"),
  HALF_DIMINISHED("m7b5", "m7b5", ChordGroup.SEVENTH, "0:1 3:3 6:5 10:7"),
  DIMINISHED7("dim7", "dim7", ChordGroup.SEVENTH, "0:1 3:3 6:5 9:7"),
  MINOR_MAJOR7("m(maj7)", "m(maj7)", ChordGroup.SEVENTH, "0:1 3:3 7:5 11:7"),
  SEVEN_SUS4("7sus4", "7sus4", ChordGroup.SEVENTH, "0:1 5:4 7:5 10:7"),
  SIX("6", "6", ChordGroup.EXTENDED, "0:1 4:3 7:5 9:6"),
  MINOR6("m6", "m6", ChordGroup.EXTENDED, "0:1 3:3 7:5 9:6"),
  MAJOR9("maj9", "maj9", ChordGroup.EXTENDED, "0:1 4:3 7:5 11:7 14:9"),
  NINE("9", "9", ChordGroup.EXTENDED, "0:1 4:3 7:5 10:7 14:9"),
  MINOR9("m9", "m9", ChordGroup.EXTENDED, "0:1 3:3 7:5 10:7 14:9"),
  THIRTEEN("13", "13", ChordGroup.EXTENDED, "0:1 4:3 10:7 14:9 21:13"),
  SEVEN_FLAT9("7b9", "7b9", ChordGroup.EXTENDED, "0:1 4:3 7:5 10:7 13:9"),
  SEVEN_SHARP9("7#9", "7#9", ChordGroup.EXTENDED, "0:1 4:3 7:5 10:7 15:9"),
  SEVEN_SHARP11("7#11", "7#11", ChordGroup.EXTENDED, "0:1 4:3 7:5 10:7 18:11"),
  ALTERED("7alt", "7alt", ChordGroup.EXTENDED, "0:1 4:3 10:7 13:9 15:9 20:13"),
  ;

  val tones: List<ChordTone> = tones.split(' ').map { it.split(':').let { (s, d) -> ChordTone(s.toInt(), d.toInt()) } }

  /** Voicing only changes chords of three notes or more. */
  val voiced: Boolean get() = tones.size >= 3
}

/** Where a chord's notes sit above its root. */
enum class Voicing(val label: String) {
  /** Every note within the octave above the root. */
  CLOSE("Close"),

  /** The root alone, the rest an octave up, so the chord rings more openly. */
  SPREAD("Spread"),

  /** The root with only the 3rd and 7th (or 6th), the notes that say what the chord is. */
  SHELL("Shell"),
}

/** The semitones above the root that [quality] sounds in [voicing], lowest first. */
fun voice(quality: ChordQuality, voicing: Voicing): List<Int> {
  val close = quality.tones.map { if (it.semitones >= 12) it.semitones - 12 else it.semitones }.distinct().sorted()
  if (!quality.voiced) return close
  return when (voicing) {
    Voicing.CLOSE -> close

    Voicing.SPREAD -> listOf(0) + close.drop(1).map { it + 12 }

    Voicing.SHELL -> {
      // Sus chords stand their 4th or 2nd in for the 3rd; chords without a 7th use their 6th, or else their 5th.
      fun first(vararg degrees: Int) = degrees.asList().firstNotNullOf { d -> quality.tones.firstOrNull { it.degree == d } }.semitones % 12
      listOf(0, first(3, 4, 2), first(7, 6, 5)).sorted()
    }
  }
}

/** The piano notes [quality] sounds in [voicing] above the MIDI note [rootMidi], such as C3 E3 G3 B3. */
fun pianoNotes(rootMidi: Int, quality: ChordQuality, voicing: Voicing): List<String> = voice(quality, voicing).map { semitones ->
  val midi = rootMidi + semitones
  Key.MAJOR_SPELLING[midi % 12] + (midi / 12 - 1)
}

/** [quality]'s notes spelled from a root named [root] (such as "Bb"): each by its degree, so Bb7 is Bb D F Ab. */
fun spell(root: String, quality: ChordQuality): List<String> {
  val letter = LETTERS.indexOf(root[0])
  val rootPitch = Key.pitchClassOf(root) ?: return emptyList()
  return quality.tones.map { tone ->
    val name = LETTERS[(letter + tone.degree - 1) % 7]
    val natural = Key.pitchClassOf(name.toString())!!
    val shift = Math.floorMod(rootPitch + tone.semitones - natural + 6, 12) - 6
    name + when (shift) {
      -2 -> "bb"
      -1 -> "b"
      1 -> "#"
      2 -> "x"
      else -> ""
    }
  }
}

private const val LETTERS = "CDEFGAB"
