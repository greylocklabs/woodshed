package com.larakelley.woodshed.domain

/** Instrument families as written in the seed data's `families` arrays. */
enum class Family(val seedName: String, val displayName: String) {
  SAXOPHONE("Saxophone", "Sax"),
  CLARINET("Clarinet", "Clarinet"),
  FLUTE("Flute", "Flute"),
  BRASS("Brass", "Brass");

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

/** A horn someone plays. Choosing one sets the key the music is written in and which exercises make sense for it. */
enum class Instrument(val label: String, val transposition: Transposition, val family: Family) {
  FLUTE("Flute", Transposition.C, Family.FLUTE),
  ALTO_FLUTE("Alto flute", Transposition.G, Family.FLUTE),
  CLARINET("Clarinet", Transposition.Bb, Family.CLARINET),
  SOPRANO_SAX("Soprano sax", Transposition.Bb, Family.SAXOPHONE),
  ALTO_SAX("Alto sax", Transposition.Eb, Family.SAXOPHONE),
  TENOR_SAX("Tenor sax", Transposition.Bb, Family.SAXOPHONE),
  BARI_SAX("Bari sax", Transposition.Eb, Family.SAXOPHONE),
  TRUMPET("Trumpet", Transposition.Bb, Family.BRASS),
  FLUGELHORN("Flugelhorn", Transposition.Bb, Family.BRASS),
  TROMBONE("Trombone", Transposition.C, Family.BRASS),
  FRENCH_HORN("French horn", Transposition.F, Family.BRASS),
  TUBA("Tuba", Transposition.C, Family.BRASS),
}
