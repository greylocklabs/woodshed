package com.larakelley.woodshed.domain

/** Instrument families as written in the seed data's `families` arrays. */
enum class Family(val seedName: String) {
  SAXOPHONE("Saxophone"),
  CLARINET("Clarinet"),
  FLUTE("Flute"),
  BRASS("Brass");

  companion object {
    fun fromSeedName(name: String): Family? = entries.firstOrNull { it.seedName == name }
  }
}

/**
 * A horn and the distance from concert pitch to what it reads. Written key = concert key + [offset], mod 12.
 * [builtIn] is the concert pitch that reads as written C, which is how the horn is named.
 */
enum class Instrument(val displayName: String, val family: Family, val builtIn: String, val offset: Int) {
  SOPRANO_SAX("Soprano sax", Family.SAXOPHONE, "Bb", 2),
  ALTO_SAX("Alto sax", Family.SAXOPHONE, "Eb", 9),
  TENOR_SAX("Tenor sax", Family.SAXOPHONE, "Bb", 2),
  BARI_SAX("Bari sax", Family.SAXOPHONE, "Eb", 9),
  CLARINET("Clarinet", Family.CLARINET, "Bb", 2),
  BASS_CLARINET("Bass clarinet", Family.CLARINET, "Bb", 2),
  PICCOLO("Piccolo", Family.FLUTE, "C", 0),
  FLUTE("Flute", Family.FLUTE, "C", 0),
  ALTO_FLUTE("Alto flute", Family.FLUTE, "G", 5),
  TRUMPET("Trumpet", Family.BRASS, "Bb", 2),
  TROMBONE("Trombone", Family.BRASS, "C", 0),
}
