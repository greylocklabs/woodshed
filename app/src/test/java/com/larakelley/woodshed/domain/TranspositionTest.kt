package com.larakelley.woodshed.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TranspositionTest {
  @Test
  fun keysAreCaptionedWithTheInstrumentsIPlay() {
    val combo = setOf(Instrument.TRUMPET, Instrument.TENOR_SAX, Instrument.BASS, Instrument.PIANO, Instrument.DRUMS)
    assertEquals("Concert: bass, piano, drums", Transposition.C.caption(combo))
    assertEquals("Tenor sax, trumpet", Transposition.Bb.caption(combo))
    // A key none of them read in keeps its examples.
    assertEquals(Transposition.Eb.examples, Transposition.Eb.caption(combo))
    assertEquals("Concert: flute", Transposition.C.caption(setOf(Instrument.FLUTE)))
    // A proper name stays capitalized partway through.
    assertEquals("Concert: tuba", Transposition.C.caption(setOf(Instrument.TUBA, Instrument.FRENCH_HORN)))
    assertEquals("French horn", Transposition.F.caption(setOf(Instrument.FRENCH_HORN)))
  }

  @Test
  fun everyInstrumentOrNoneShowsTheExamples() {
    for (key in Transposition.entries) {
      assertEquals(key.examples, key.caption(Instrument.entries))
      assertEquals(key.examples, key.caption(emptySet()))
    }
  }
}
