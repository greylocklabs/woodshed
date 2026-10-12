package com.larakelley.woodshed.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyTest {
  @Test
  fun eachTranspositionReadsItsOwnPitchAsWrittenC() {
    // A wrong offset makes every displayed key silently wrong; this is the check that catches it.
    for (transposition in Transposition.entries) {
      val builtIn = requireNotNull(Key.of(transposition.name, Tonality.MAJOR))
      assertEquals(transposition.name, "C", builtIn.writtenFor(transposition).displayName)
    }
  }

  @Test
  fun knownTranspositions() {
    val cases = listOf(
      Triple(Key.of("C", Tonality.MAJOR), Transposition.Eb, "A"),
      Triple(Key.of("Bb", Tonality.MAJOR), Transposition.Bb, "C"),
      Triple(Key.of("F", Tonality.MAJOR), Transposition.Bb, "G"),
      Triple(Key.of("Eb", Tonality.MAJOR), Transposition.G, "Ab"),
      Triple(Key.of("Eb", Tonality.MAJOR), Transposition.F, "Bb"),
      Triple(Key.of("G", Tonality.MINOR), Transposition.Eb, "E minor"),
      Triple(Key.of("Eb", Tonality.MINOR), Transposition.Bb, "F minor"),
      Triple(Key.of("Db", Tonality.MAJOR), Transposition.C, "Db"),
    )
    for ((concert, transposition, written) in cases) {
      assertEquals("$concert in $transposition", written, concert!!.writtenFor(transposition).displayName)
    }
  }

  @Test
  fun everyKeyInEveryTranspositionInBothModes() {
    // 5 transpositions x 12 keys x 2 modes = 120 cases.
    var checked = 0
    for (transposition in Transposition.entries) {
      for (pc in 0..11) {
        for (minor in listOf(false, true)) {
          val written = Key(pc, minor).writtenFor(transposition)
          val expectedPc = (pc + transposition.offset) % 12
          val table = if (minor) Key.MINOR_SPELLING else Key.MAJOR_SPELLING
          val expected = if (minor) "${table[expectedPc]} minor" else table[expectedPc]
          assertEquals(expected, written.displayName)
          assertEquals(Key(pc, minor), written.transpose(-transposition.offset))
          checked++
        }
      }
    }
    assertEquals(120, checked)
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
    assertEquals(Key(3, false), Key.of("Eb", Tonality.MAJOR))
    assertEquals(Key(5, true), Key.of("F", Tonality.MINOR))
    assertEquals(Key(1, true), Key.of("C#", Tonality.MINOR))
    assertEquals(Key(11, false), Key.of("B", Tonality.MAJOR))
    assertEquals(Tonality.MINOR, Tonality.fromSeedName("minor"))
  }

  @Test
  fun unparseableKeysAreNullNotGuessed() {
    for (raw in listOf("Eb, C", "", "H", "eb", "C minor", "C-", "Cb-x", null)) {
      assertNull(raw, Key.pitchClassOf(raw))
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
