package com.larakelley.woodshed.ui.browse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlphabetStripTest {
  private val keys = listOf("26-2", "all the things you are", "autumn leaves", "body and soul", "donna lee", "solar")

  @Test
  fun lettersFileDigitsUnderHash() {
    assertEquals('#', letterOf("26-2"))
    assertEquals('A', letterOf("autumn leaves"))
    assertEquals('#', letterOf(""))
  }

  @Test
  fun jumpsToTheFirstTuneUnderALetterOrTheNextLetterWithTunes() {
    assertEquals(0, firstIndexFor('#', keys))
    assertEquals(1, firstIndexFor('A', keys))
    assertEquals(4, firstIndexFor('C', keys)) // no C tunes, so the first D
    assertNull(firstIndexFor('T', keys))
  }

  @Test
  fun aTouchOnALettersCentreSelectsThatLetter() {
    val height = 2_800f
    ALPHABET.forEachIndexed { i, letter ->
      val centre = height * (i + 1) / (ALPHABET.size + 1)
      assertEquals("$letter", i, letterIndexAt(centre, height))
    }
    assertEquals(0, letterIndexAt(0f, height))
    assertEquals(ALPHABET.lastIndex, letterIndexAt(height, height))
  }
}
