package com.larakelley.woodshed.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordTest {
  @Test
  fun chordsAreSpelledByDegree() {
    assertEquals(listOf("Bb", "D", "F", "Ab"), spell("Bb", ChordQuality.DOMINANT7))
    assertEquals(listOf("C", "Eb", "Gb", "Bbb"), spell("C", ChordQuality.DIMINISHED7))
    assertEquals(listOf("F#", "A#", "C#", "E", "G"), spell("F#", ChordQuality.SEVEN_FLAT9))
    assertEquals(listOf("Eb", "G", "Bb", "Db", "F#"), spell("Eb", ChordQuality.SEVEN_SHARP9))
    assertEquals(listOf("G", "B", "F", "Ab", "A#", "Eb"), spell("G", ChordQuality.ALTERED))
    assertEquals(listOf("Db"), spell("Db", ChordQuality.NOTE))
  }

  @Test
  fun voicingsPlaceTheNotes() {
    assertEquals(listOf(0, 4, 7, 10), voice(ChordQuality.DOMINANT7, Voicing.CLOSE))
    assertEquals(listOf(0, 16, 19, 22), voice(ChordQuality.DOMINANT7, Voicing.SPREAD))
    assertEquals(listOf(0, 4, 10), voice(ChordQuality.DOMINANT7, Voicing.SHELL))
    // A ninth sits inside the octave when voiced close.
    assertEquals(listOf(0, 2, 3, 7, 10), voice(ChordQuality.MINOR9, Voicing.CLOSE))
    // Sus chords stand the 4th in for the 3rd; sixth chords the 6th for the 7th; triads their 5th.
    assertEquals(listOf(0, 5, 10), voice(ChordQuality.SEVEN_SUS4, Voicing.SHELL))
    assertEquals(listOf(0, 3, 9), voice(ChordQuality.MINOR6, Voicing.SHELL))
    assertEquals(listOf(0, 4, 7), voice(ChordQuality.MAJOR, Voicing.SHELL))
    // The note alone and the fifth ignore the voicing.
    assertEquals(listOf(0, 7), voice(ChordQuality.FIFTH, Voicing.SPREAD))
  }

  @Test
  fun everyChordFitsTheDrone() {
    for (quality in ChordQuality.entries) {
      for (voicing in Voicing.entries) {
        val notes = voice(quality, voicing)
        assertEquals("$quality $voicing", 0, notes.first())
        assertTrue("$quality $voicing", notes.size in 1..6 && notes == notes.sorted().distinct())
      }
    }
  }
}
