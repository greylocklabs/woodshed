package com.larakelley.woodshed.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyTest {
  @Test
  fun eachInstrumentReadsItsBuiltInPitchAsWrittenC() {
    // A wrong offset makes every displayed key silently wrong; this is the check that catches it.
    for (instrument in Instrument.entries) {
      val builtIn = requireNotNull(Key.parse(instrument.builtIn))
      assertEquals(instrument.name, "C", builtIn.writtenFor(instrument).displayName)
    }
  }

  @Test
  fun knownTranspositions() {
    val cases = listOf(
      Triple("C", Instrument.ALTO_SAX, "A"),
      Triple("Bb", Instrument.TENOR_SAX, "C"),
      Triple("F", Instrument.TRUMPET, "G"),
      Triple("Eb", Instrument.ALTO_FLUTE, "Ab"),
      Triple("G-", Instrument.ALTO_SAX, "E minor"),
      Triple("Eb-", Instrument.TENOR_SAX, "F minor"),
      Triple("Db", Instrument.TROMBONE, "Db"),
    )
    for ((concert, instrument, written) in cases) {
      assertEquals("$concert on $instrument", written, Key.parse(concert)!!.writtenFor(instrument).displayName)
    }
  }

  @Test
  fun everyKeyOnEveryInstrumentInBothModes() {
    // 11 instruments x 12 keys x 2 modes = 264 cases.
    var checked = 0
    for (instrument in Instrument.entries) {
      for (pc in 0..11) {
        for (minor in listOf(false, true)) {
          val written = Key(pc, minor).writtenFor(instrument)
          val expectedPc = (pc + instrument.offset) % 12
          val table = if (minor) Key.MINOR_SPELLING else Key.MAJOR_SPELLING
          val expected = if (minor) "${table[expectedPc]} minor" else table[expectedPc]
          assertEquals(expected, written.displayName)
          assertEquals(Key(pc, minor), written.transpose(-instrument.offset))
          checked++
        }
      }
    }
    assertEquals(264, checked)
  }

  @Test
  fun minorSpellsWithSharpsAndMajorWithFlats() {
    assertEquals("C# minor", Key(1, minor = true).displayName)
    assertEquals("Db", Key(1, minor = false).displayName)
    assertEquals("F# minor", Key(6, minor = true).displayName)
    assertEquals("Gb", Key(6, minor = false).displayName)
  }

  @Test
  fun parsesSeedFormat() {
    assertEquals(Key(3, false), Key.parse("Eb"))
    assertEquals(Key(5, true), Key.parse("F-"))
    assertEquals(Key(1, true), Key.parse("C#-"))
    assertEquals(Key(11, false), Key.parse("B"))
  }

  @Test
  fun unparseableKeysAreNullNotGuessed() {
    for (raw in listOf("Eb, C", "", "H", "eb", "C minor", "Cb-x", null)) {
      assertNull(raw, Key.parse(raw))
    }
  }

  @Test
  fun circleOfFourthsOrder() {
    assertEquals(
      listOf("C", "F", "Bb", "Eb", "Ab", "Db", "Gb", "B", "E", "A", "D", "G"),
      Key.CIRCLE_OF_FOURTHS.map { Key.MAJOR_SPELLING[it] },
    )
  }
}
