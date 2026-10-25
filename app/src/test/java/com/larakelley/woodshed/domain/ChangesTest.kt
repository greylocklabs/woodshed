package com.larakelley.woodshed.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangesTest {
  private fun names(bars: List<Bar>, tonic: Int) = bars.map { bar -> bar.chords.joinToString(" ") { it.name(tonic) } }

  @Test
  fun numeralsReadRelativeToTheKey() {
    // In F.
    assertEquals(listOf("Gm7 C7", "Fmaj7"), names(bars("IIm7 V7 | Imaj7"), 5))
    assertEquals(listOf("Bdim7", "Ebm7b5", "Db7"), names(bars("#IVdim7 | bVIIm7b5 | bVI7"), 5))
    // A bare numeral is a major triad.
    assertEquals(listOf("Bb", "Dm"), names(bars("IV | VIm"), 5))
  }

  @Test
  fun theLibraryPlaysAsWritten() {
    val f = 5
    assertEquals(
      listOf("F7", "F7", "F7", "F7", "Bb7", "Bb7", "F7", "F7", "C7", "Bb7", "F7", "C7"),
      names(ChangesLibrary.form("blues")!!.bars, f),
    )
    assertEquals("Bb7", names(ChangesLibrary.form("quick-blues")!!.bars, f)[1])
    assertEquals(listOf("Gm7b5", "C7b9", "Fm6", "Fm6"), names(ChangesLibrary.form("minor-two-five-one")!!.bars, f))
    val lengths = mapOf("rhythm-changes" to 32, "modal-vamp" to 32, "coltrane" to 16, "jazz-blues" to 12, "eight-bar-blues" to 8, "sus-vamp" to 16)
    lengths.forEach { (id, length) -> assertEquals(id, length, ChangesLibrary.form(id)!!.bars.size) }
    // Giant Steps' first bars, in B.
    assertEquals(listOf("Bmaj7 D7", "Gmaj7 Bb7", "Ebmaj7"), names(ChangesLibrary.form("coltrane")!!.bars.take(3), 11))
    assertEquals(ChangesLibrary.forms.size, ChangesLibrary.forms.map { it.id }.toSet().size)
  }

  @Test
  fun everyMoveVisitsEveryKey() {
    for (move in Move.entries) {
      val keys = move.keys()
      assertEquals(move.name, (0..11).toSet(), keys.toSet())
      assertEquals(move.name, 0, keys.first())
    }
    assertEquals(listOf(0, 10, 8, 6, 4, 2, 11, 9, 7, 5, 3, 1), Move.WHOLE_DOWN.keys())
    assertEquals(listOf(0, 5, 10, 0, 5, 10, 3, 8, 1), Move.THREES.keys().take(9))
    // A random order changes from one time through to the next.
    assertTrue(Move.RANDOM.keys(1) != Move.RANDOM.keys(2))
  }

  @Test
  fun patternsPlayTheirUnitInEachKey() {
    val circle = ChangesLibrary.pattern("circle")!!
    assertEquals(listOf("F7", "F7", "Bb7", "Bb7", "Eb7"), names(circle.bars(), 5).take(5))
    val twoFiveOnes = ChangesLibrary.pattern("two-five-ones")!!
    assertEquals(48, twoFiveOnes.bars().size)
    assertEquals(listOf("Dm7", "G7", "Cmaj7", "Cmaj7", "Cm7"), names(twoFiveOnes.bars(), 0).take(5))
    assertEquals(listOf("Dm7 G7", "Cm7 F7"), names(ChangesLibrary.pattern("two-five-chain")!!.bars(), 0).take(2))
    val blues = circle.copy(unit = PatternUnit.FORM, form = ChangesLibrary.form("blues"))
    assertEquals(144, blues.bars().size)
    assertEquals("Bb7", names(blues.bars(), 5)[12])
  }

  @Test
  fun theTimelineCountsInThenPlaysBarByBar() {
    var keepGoing = true
    val timeline = ChangesTimeline(4, 4, { keepGoing }) { bars("IIm7 V7 | Imaj7") }
    assertEquals(ChangesBeat.CountIn(3), timeline.at(3))
    // A split bar's first chord takes beats 1 and 2, its second 3 and 4.
    assertEquals("Dm7", (timeline.at(5) as ChangesBeat.Playing).chord.name(0))
    assertEquals("G7", (timeline.at(6) as ChangesBeat.Playing).chord.name(0))
    assertEquals(ChangesBeat.Playing(0, 1, 0, bars("Imaj7")[0].chords[0]), timeline.at(8))
    assertEquals(1, (timeline.at(12) as ChangesBeat.Playing).chorus)
    keepGoing = false
    assertEquals(ChangesBeat.Done, timeline.at(12))
    // In 3/4, the larger half is the first.
    val waltz = ChangesTimeline(3, 0, { true }) { bars("I V") }
    assertEquals(listOf("C", "C", "G"), (0L..2L).map { (waltz.at(it) as ChangesBeat.Playing).chord.name(0) })
  }
}
