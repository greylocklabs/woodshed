package com.larakelley.woodshed.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyTest {
  @Test
  fun eachTranspositionReadsItsOwnPitchAsWrittenC() {
    // A wrong offset makes every displayed key silently wrong; this is the check that catches it.
    for (transposition in Transposition.entries) {
      val builtIn = requireNotNull(Key.parse(transposition.name))
      assertEquals(transposition.name, "C", builtIn.writtenFor(transposition).displayName)
    }
  }

  @Test
  fun knownTranspositions() {
    val cases = listOf(
      Triple("C", Transposition.Eb, "A"),
      Triple("Bb", Transposition.Bb, "C"),
      Triple("F", Transposition.Bb, "G"),
      Triple("Eb", Transposition.G, "Ab"),
      Triple("Eb", Transposition.F, "Bb"),
      Triple("G-", Transposition.Eb, "E minor"),
      Triple("Eb-", Transposition.Bb, "F minor"),
      Triple("Db", Transposition.C, "Db"),
    )
    for ((concert, transposition, written) in cases) {
      assertEquals("$concert in $transposition", written, Key.parse(concert)!!.writtenFor(transposition).displayName)
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
