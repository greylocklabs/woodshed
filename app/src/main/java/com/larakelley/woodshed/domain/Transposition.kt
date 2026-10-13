package com.larakelley.woodshed.domain

/** Instrument families as written in the seed data's `families` arrays. */
enum class Family(val seedName: String, val displayName: String) {
  SAXOPHONE("Saxophone", "Sax"),
  CLARINET("Clarinet", "Clarinet"),
  FLUTE("Flute", "Flute"),
  BRASS("Brass", "Brass"),
  ;

  companion object {
    fun fromSeedName(name: String): Family? = entries.firstOrNull { it.seedName == name }
  }
}

/**
 * The pitch a horn is built in, which sets what it reads: written key = concert key + [offset], mod 12.
 * Concert [name] reads as written C on that horn, which is how the transposition is named.
 */
enum class Transposition(val offset: Int, val examples: String) {
  C(0, "Concert: flute, trombone, piano"),
  Bb(2, "Trumpet, tenor and soprano sax, clarinet"),
  Eb(9, "Alto and bari sax"),
  F(7, "French horn"),
  G(5, "Alto flute"),
}

/** How the settings list groups instruments. */
enum class InstrumentGroup(val label: String) {
  WOODWINDS("Woodwinds"),
  SAXOPHONES("Saxophones"),
  BRASS("Brass"),
  STRINGS("Strings"),
  KEYS_AND_PERCUSSION("Keys and percussion"),
  VOICE("Voice"),
}

/**
 * An instrument someone plays. Choosing one sets the key music is written in and which exercises make sense.
 * [family] is null where the exercises have no family of their own, so only exercises for everyone apply;
 * [breath] is false for instruments you don't blow or sing, which skip tone, breathing and tonguing work.
 */
enum class Instrument(val label: String, val group: InstrumentGroup, val transposition: Transposition, val family: Family?, val breath: Boolean = true) {
  FLUTE("Flute", InstrumentGroup.WOODWINDS, Transposition.C, Family.FLUTE),
  PICCOLO("Piccolo", InstrumentGroup.WOODWINDS, Transposition.C, Family.FLUTE),
  ALTO_FLUTE("Alto flute", InstrumentGroup.WOODWINDS, Transposition.G, Family.FLUTE),
  CLARINET("Clarinet", InstrumentGroup.WOODWINDS, Transposition.Bb, Family.CLARINET),
  BASS_CLARINET("Bass clarinet", InstrumentGroup.WOODWINDS, Transposition.Bb, Family.CLARINET),
  SOPRANO_SAX("Soprano sax", InstrumentGroup.SAXOPHONES, Transposition.Bb, Family.SAXOPHONE),
  ALTO_SAX("Alto sax", InstrumentGroup.SAXOPHONES, Transposition.Eb, Family.SAXOPHONE),
  TENOR_SAX("Tenor sax", InstrumentGroup.SAXOPHONES, Transposition.Bb, Family.SAXOPHONE),
  BARI_SAX("Bari sax", InstrumentGroup.SAXOPHONES, Transposition.Eb, Family.SAXOPHONE),
  TRUMPET("Trumpet", InstrumentGroup.BRASS, Transposition.Bb, Family.BRASS),
  CORNET("Cornet", InstrumentGroup.BRASS, Transposition.Bb, Family.BRASS),
  FLUGELHORN("Flugelhorn", InstrumentGroup.BRASS, Transposition.Bb, Family.BRASS),
  TROMBONE("Trombone", InstrumentGroup.BRASS, Transposition.C, Family.BRASS),
  FRENCH_HORN("French horn", InstrumentGroup.BRASS, Transposition.F, Family.BRASS),
  EUPHONIUM("Euphonium", InstrumentGroup.BRASS, Transposition.C, Family.BRASS),
  TUBA("Tuba", InstrumentGroup.BRASS, Transposition.C, Family.BRASS),
  GUITAR("Guitar", InstrumentGroup.STRINGS, Transposition.C, null, breath = false),
  BASS("Bass", InstrumentGroup.STRINGS, Transposition.C, null, breath = false),
  VIOLIN("Violin", InstrumentGroup.STRINGS, Transposition.C, null, breath = false),
  VIOLA("Viola", InstrumentGroup.STRINGS, Transposition.C, null, breath = false),
  CELLO("Cello", InstrumentGroup.STRINGS, Transposition.C, null, breath = false),
  PIANO("Piano", InstrumentGroup.KEYS_AND_PERCUSSION, Transposition.C, null, breath = false),
  ORGAN("Organ", InstrumentGroup.KEYS_AND_PERCUSSION, Transposition.C, null, breath = false),
  VIBRAPHONE("Vibraphone", InstrumentGroup.KEYS_AND_PERCUSSION, Transposition.C, null, breath = false),
  DRUMS("Drums", InstrumentGroup.KEYS_AND_PERCUSSION, Transposition.C, null, breath = false),
  VOICE("Voice", InstrumentGroup.VOICE, Transposition.C, null),
  ;

  /** Whether an exercise scoped to [families] (empty means everyone) in [category] makes sense on this instrument. */
  fun suits(families: List<Family>, category: String): Boolean {
    if (!breath && category in BREATH_CATEGORIES) return false
    return families.isEmpty() || (family != null && family in families)
  }

  companion object {
    private val BREATH_CATEGORIES = setOf("Tone", "Air & Breathing", "Articulation")
  }
}
