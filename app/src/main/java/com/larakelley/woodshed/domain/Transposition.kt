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
