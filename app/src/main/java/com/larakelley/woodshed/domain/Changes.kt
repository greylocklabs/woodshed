package com.larakelley.woodshed.domain

import kotlin.random.Random

/** A chord in a progression: its root in semitones above the key's tonic, and its quality. */
data class ProgressionChord(val root: Int, val quality: ChordQuality) {
  /** The chord named in a key whose tonic is [tonic] (concert or written), as "Bb7". */
  fun name(tonic: Int): String = Key.MAJOR_SPELLING[Math.floorMod(tonic + root, 12)] + quality.symbol
}

/** One bar: one chord, or two that split it. */
data class Bar(val chords: List<ProgressionChord>) {
  init {
    require(chords.size in 1..2) { "a bar holds one or two chords, not ${chords.size}" }
  }

  /** The chord sounding on [beat] (from 0) of a bar [beatsPerBar] long; a split bar's first chord takes the larger half. */
  fun chordOn(beat: Int, beatsPerBar: Int): ProgressionChord = if (chords.size == 1 || beat < (beatsPerBar + 1) / 2) chords[0] else chords[1]
}

/** How the Changes tab groups what it offers. */
enum class ChangesSection(val label: String) {
  BLUES("Blues"),
  FORMS("Forms"),
  CADENCES("Cadences and turnarounds"),
  PATTERNS("Patterns"),
  MORE("More"),
}

/** A progression to play over, written relative to its key so it plays in any. */
data class Form(
  val id: String,
  val name: String,
  val description: String,
  val section: ChangesSection,
  val bars: List<Bar>,
  /** Named for its minor key, as "Fm". */
  val minor: Boolean = false,
  val advanced: Boolean = false,
)

/** How a pattern moves from key to key. */
enum class Move(val label: String, private val step: Int) {
  FOURTHS("Fourths", 5),
  FIFTHS("Fifths", 7),
  HALF_UP("Half steps up", 1),
  HALF_DOWN("Half steps down", -1),
  WHOLE_DOWN("Whole steps", -2),
  MINOR_THIRDS("Minor thirds", 3),
  MAJOR_THIRDS("Major thirds", 4),

  /** Around the circle in fourths, each three keys played twice before moving on. */
  THREES("Keys in threes", 5),

  /** All 12 keys in a new order each time through. */
  RANDOM("Random keys", 0),
  ;

  /**
   * The keys in order, in semitones above the first, for time through [chorus] (from 0). Steps that come back
   * round before all 12, such as whole steps, play their keys and then go again from a half step over.
   */
  fun keys(chorus: Int = 0): List<Int> = when (this) {
    // The first key stays put, so a random run still starts in the key asked for.
    RANDOM -> listOf(0) + (1..11).shuffled(Random(chorus))

    THREES -> FOURTHS.keys().chunked(3).flatMap { it + it }

    else -> {
      val cycle = 12 / gcd(12, Math.floorMod(step, 12))
      val over = if (step < 0) -1 else 1
      List(12) { i -> Math.floorMod(i / cycle * over + i % cycle * step, 12) }
    }
  }
}

/** What a pattern plays in each key. */
enum class PatternUnit(val label: String) {
  ONE_CHORD("One chord"),
  TWO_FIVE("ii-V"),
  TWO_FIVE_ONE("ii-V-I"),
  FORM("A form"),
}

/** A key-to-key drill: [unit] played in each key [move] goes through. */
data class Pattern(
  val id: String,
  val name: String,
  val description: String,
  val move: Move,
  val unit: PatternUnit,
  val chord: ChordQuality = ChordQuality.DOMINANT7,
  /** Bars spent in each key; a form takes as long as it is. */
  val barsPerKey: Int = 2,
  val form: Form? = null,
) {
  /** The bars of time through [chorus], relative to the first key. */
  fun bars(chorus: Int = 0): List<Bar> = move.keys(chorus).flatMap { key ->
    unitBars().map { bar -> Bar(bar.chords.map { it.copy(root = Math.floorMod(it.root + key, 12)) }) }
  }

  private fun unitBars(): List<Bar> = when (unit) {
    PatternUnit.ONE_CHORD -> List(barsPerKey) { Bar(listOf(ProgressionChord(0, chord))) }

    PatternUnit.TWO_FIVE -> if (barsPerKey <= 1) listOf(bar("IIm7 V7")) else List(barsPerKey) { if (it < barsPerKey / 2) bar("IIm7") else bar("V7") }

    // The short ii-V-I puts ii and V in one bar; the long one gives each a bar and the I two.
    PatternUnit.TWO_FIVE_ONE -> if (barsPerKey <= 2) bars("IIm7 V7 | Imaj7") else bars("IIm7 | V7 | Imaj7 | Imaj7")

    PatternUnit.FORM -> form?.bars ?: emptyList()
  }
}

/**
 * Bars written as Roman numerals relative to the key, "|" between bars and a space between the two chords of a
 * split bar. A numeral's case says nothing; the chord symbol after it does, so "IIm7 V7 | Imaj7" is a ii-V-I.
 */
fun bars(text: String): List<Bar> = text.split('|').map { bar(it) }

/** [bars] written back as [bars] reads them, as "IIm7 V7 | Imaj7". */
fun numerals(bars: List<Bar>): String = bars.joinToString(" | ") { bar ->
  bar.chords.joinToString(" ") { NUMERALS[it.root] + if (it.quality == ChordQuality.MAJOR) "" else it.quality.symbol }
}

/** Each root above the key as written; a tritone over the key reads as #IV, as in a jazz blues. */
private val NUMERALS = listOf("I", "bII", "II", "bIII", "III", "IV", "#IV", "V", "bVI", "VI", "bVII", "VII")

private fun bar(text: String): Bar = Bar(text.trim().split(Regex("\\s+")).map(::chord))

private val NUMERAL = Regex("^([b#]?)(VII|VI|V|IV|III|II|I)(.*)$")
private val DEGREES = mapOf("I" to 0, "II" to 2, "III" to 4, "IV" to 5, "V" to 7, "VI" to 9, "VII" to 11)

private fun chord(token: String): ProgressionChord {
  val (accidental, numeral, symbol) = requireNotNull(NUMERAL.matchEntire(token)) { "not a chord: $token" }.destructured
  // A bare numeral is a major triad, never the drone's single note.
  val quality = if (symbol.isEmpty()) {
    ChordQuality.MAJOR
  } else {
    requireNotNull(ChordQuality.entries.firstOrNull { it.symbol == symbol }) { "no chord quality $symbol in $token" }
  }
  val shift = if (accidental == "b") {
    -1
  } else if (accidental == "#") {
    1
  } else {
    0
  }
  return ProgressionChord(Math.floorMod(DEGREES.getValue(numeral) + shift, 12), quality)
}

private fun repeated(chord: String, times: Int) = List(times) { chord }.joinToString(" | ")

private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

/** The forms and patterns the Changes tab offers out of the box. */
object ChangesLibrary {
  private val rhythmA = "I6 VI7 | IIm7 V7 | IIIm7 VI7 | IIm7 V7 | I7 | IV #IVdim7"

  val forms: List<Form> = listOf(
    Form("blues", "12-bar blues", "I, IV and V", ChangesSection.BLUES, bars("I7 | I7 | I7 | I7 | IV7 | IV7 | I7 | I7 | V7 | IV7 | I7 | V7")),
    Form("quick-blues", "Quick-change blues", "IV in bar 2", ChangesSection.BLUES, bars("I7 | IV7 | I7 | I7 | IV7 | IV7 | I7 | I7 | V7 | IV7 | I7 | V7")),
    Form(
      "jazz-blues",
      "Jazz blues",
      "With ii-Vs, a #IV dim and a turnaround",
      ChangesSection.BLUES,
      bars("I7 | IV7 | I7 | Vm7 I7 | IV7 | #IVdim7 | I7 | IIIm7 VI7 | IIm7 | V7 | I7 VI7 | IIm7 V7"),
    ),
    Form(
      "bird-blues",
      "Bird blues",
      "ii-Vs walking down, as in Blues for Alice",
      ChangesSection.BLUES,
      bars("Imaj7 | VIIm7b5 III7 | VIm7 II7 | Vm7 I7 | IV7 | IVm7 bVII7 | IIIm7 VI7 | bIIIm7 bVI7 | IIm7 | V7 | I7 VI7 | IIm7 V7"),
    ),
    Form(
      "minor-blues",
      "Minor blues",
      "bVI7 to V7 in bars 9 and 10",
      ChangesSection.BLUES,
      bars("Im7 | IVm7 | Im7 | Im7 | IVm7 | IVm7 | Im7 | Im7 | bVI7 | V7 | Im7 | V7"),
      minor = true,
    ),
    Form("eight-bar-blues", "8-bar blues", "8 bars", ChangesSection.BLUES, bars("I7 | V7 | IV7 | IV7 | I7 | V7 | I7 IV7 | I7 V7")),
    Form(
      "rhythm-changes",
      "Rhythm changes",
      "32 bars, AABA",
      ChangesSection.FORMS,
      bars("$rhythmA | IIIm7 VI7 | IIm7 V7 | $rhythmA | IIm7 V7 | I6 | III7 | III7 | VI7 | VI7 | II7 | II7 | V7 | V7 | $rhythmA | IIm7 V7 | I6"),
    ),
    Form(
      "rhythm-bridge",
      "Rhythm changes bridge",
      "III7, VI7, II7, V7, two bars each",
      ChangesSection.FORMS,
      bars("III7 | III7 | VI7 | VI7 | II7 | II7 | V7 | V7"),
    ),
    Form(
      "modal-vamp",
      "Modal vamp",
      "16 bars, up a half step for 8, back for 8, as in So What",
      ChangesSection.FORMS,
      bars("${repeated("Im7", 16)} | ${repeated("bIIm7", 8)} | ${repeated("Im7", 8)}"),
      minor = true,
    ),
    Form(
      "sus-vamp",
      "Sus vamp",
      "Four sus chords, 4 bars each, like Maiden Voyage",
      ChangesSection.FORMS,
      bars("${repeated("I7sus4", 4)} | ${repeated("bIII7sus4", 4)} | ${repeated("bII7sus4", 4)} | ${repeated("VII7sus4", 4)}"),
    ),
    Form(
      "coltrane",
      "Coltrane changes",
      "Major thirds, as in Giant Steps",
      ChangesSection.FORMS,
      bars(
        "Imaj7 bIII7 | bVImaj7 VII7 | IIImaj7 | bVIIm7 bIII7 | bVImaj7 VII7 | IIImaj7 V7 | Imaj7 | bVm7 VII7 | " +
          "IIImaj7 | bVIIm7 bIII7 | bVImaj7 | IIm7 V7 | Imaj7 | bVm7 VII7 | IIImaj7 | IIm7 V7",
      ),
      advanced = true,
    ),
    Form("two-five-one", "ii-V-I", "Major", ChangesSection.CADENCES, bars("IIm7 | V7 | Imaj7 | Imaj7")),
    Form("minor-two-five-one", "ii-V-i", "Minor: m7b5, 7b9, m6", ChangesSection.CADENCES, bars("IIm7b5 | V7b9 | Im6 | Im6"), minor = true),
    Form("tritone-two-five", "ii-V with a tritone sub", "ii, bII7, I", ChangesSection.CADENCES, bars("IIm7 | bII7 | Imaj7 | Imaj7")),
    Form("backdoor", "Backdoor ii-V", "iv7, bVII7, I", ChangesSection.CADENCES, bars("IVm7 | bVII7 | Imaj7 | Imaj7")),
    Form("one-six-two-five", "I-vi-ii-V", "The common turnaround", ChangesSection.CADENCES, bars("Imaj7 VIm7 | IIm7 V7")),
    Form("three-six-two-five", "iii-VI-ii-V", "Starting on iii", ChangesSection.CADENCES, bars("IIIm7 VI7 | IIm7 V7")),
    Form("tritone-turnaround", "Tritone-sub turnaround", "I, bIII7, ii, bII7", ChangesSection.CADENCES, bars("Imaj7 bIII7 | IIm7 bII7")),
    Form("dameron", "Dameron turnaround", "I, bIII, bVI, bII, as in Lady Bird", ChangesSection.CADENCES, bars("Imaj7 bIIImaj7 | bVImaj7 bIImaj7")),
    Form("dominant-cycle", "Cycle of dominants", "III7, VI7, II7, V7 to I", ChangesSection.CADENCES, bars("III7 | VI7 | II7 | V7 | I6 | I6")),
    Form(
      "tritone-dominants",
      "Dominants with tritone subs",
      "III7, bIII7, II7, bII7 to I",
      ChangesSection.CADENCES,
      bars("III7 | bIII7 | II7 | bII7 | Imaj7 | Imaj7"),
    ),
    Form("pop", "I-V-vi-IV", "Pop", ChangesSection.MORE, bars("I | V | VIm | IV")),
    Form("doo-wop", "I-vi-IV-V", "50s doo-wop", ChangesSection.MORE, bars("I | VIm | IV | V")),
    Form("andalusian", "Andalusian cadence", "i, bVII, bVI, V", ChangesSection.MORE, bars("Im | bVII | bVI | V"), minor = true),
    Form("gospel", "Gospel vamp", "I and IV", ChangesSection.MORE, bars("I | IV")),
  )

  val patterns: List<Pattern> = listOf(
    Pattern("circle", "Around the circle", "All 12 keys, in fourths or fifths", Move.FOURTHS, PatternUnit.ONE_CHORD),
    Pattern("two-five-ones", "ii-V-Is down in whole steps", "All 12 keys, long or short ii-V-Is", Move.WHOLE_DOWN, PatternUnit.TWO_FIVE_ONE, barsPerKey = 4),
    Pattern("two-five-chain", "Chain of ii-Vs", "Each one leading into the next key", Move.WHOLE_DOWN, PatternUnit.TWO_FIVE, barsPerKey = 1),
    Pattern("half-steps", "Half steps", "All 12 keys, up or down", Move.HALF_UP, PatternUnit.ONE_CHORD),
    Pattern("whole-steps", "Whole steps", "6 keys, then the other 6", Move.WHOLE_DOWN, PatternUnit.ONE_CHORD),
    Pattern("thirds", "Minor or major thirds", "4 or 3 keys at a time", Move.MINOR_THIRDS, PatternUnit.ONE_CHORD),
    Pattern("threes", "Keys in threes", "Three nearby keys, then the next three", Move.THREES, PatternUnit.ONE_CHORD),
    Pattern("random", "Random keys", "A new key each time through", Move.RANDOM, PatternUnit.ONE_CHORD),
  )

  fun form(id: String): Form? = forms.firstOrNull { it.id == id }

  fun pattern(id: String): Pattern? = patterns.firstOrNull { it.id == id }
}

/** Where a beat falls while changes play. */
sealed interface ChangesBeat {
  /** A count-in beat, from 0, before the first bar. */
  data class CountIn(val beat: Int) : ChangesBeat

  /** A beat of [bar] (from 0) in time through [chorus], with the chord sounding on it. */
  data class Playing(val chorus: Int, val bar: Int, val beat: Int, val chord: ProgressionChord) : ChangesBeat

  /** Past the end, when the changes play once. */
  data object Done : ChangesBeat
}

/**
 * Maps beat numbers to chords: [countInBeats] beats of count-in, then bar after bar of [barsFor] time through
 * each chorus, once or round again while [keepGoing].
 */
class ChangesTimeline(
  private val beatsPerBar: Int,
  private val countInBeats: Int,
  private val keepGoing: () -> Boolean,
  private val barsFor: (chorus: Int) -> List<Bar>,
) {
  /** Every time through is as long as the first; only a random pattern's keys change from one to the next. */
  private val length = barsFor(0).size
  private var cachedChorus = -1
  private var cachedBars: List<Bar> = emptyList()

  fun bars(chorus: Int): List<Bar> {
    if (chorus != cachedChorus) {
      cachedBars = barsFor(chorus)
      cachedChorus = chorus
    }
    return cachedBars
  }

  /** Beat [index] counted from the first count-in beat. */
  fun at(index: Long): ChangesBeat {
    if (index < countInBeats) return ChangesBeat.CountIn(index.toInt())
    val played = index - countInBeats
    if (length == 0) return ChangesBeat.Done
    val barNumber = played / beatsPerBar
    val chorus = (barNumber / length).toInt()
    if (chorus > 0 && !keepGoing()) return ChangesBeat.Done
    val bar = (barNumber % length).toInt()
    val beat = (played % beatsPerBar).toInt()
    return ChangesBeat.Playing(chorus, bar, beat, bars(chorus)[bar].chordOn(beat, beatsPerBar))
  }
}
