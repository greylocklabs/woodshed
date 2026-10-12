package com.larakelley.woodshed.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SchedulingTest {
  private val day = 24 * 60 * 60 * 1000L

  @Test
  fun goodGradesGrowTheInterval() {
    var s = CardState()
    val intervals = (1..4).map { s = Sm2Scheduler.next(s, Grade.GOOD, 0); s.intervalDays }
    assertEquals(listOf(1.0, 3.0, 7.5, 18.75), intervals)
    assertEquals(4, s.reps)
    assertEquals((18.75 * day).toLong(), s.dueAt)
  }

  @Test
  fun againResetsCountsALapseAndComesBackInTheSession() {
    val learned = CardState(reps = 3, intervalDays = 7.5, ease = 2.5)
    val s = Sm2Scheduler.next(learned, Grade.AGAIN, 1_000)
    assertEquals(0, s.reps)
    assertEquals(1, s.lapses)
    assertEquals(2.3, s.ease, 1e-9)
    assertEquals(1_000 + Sm2Scheduler.AGAIN_DELAY_MS, s.dueAt)
  }

  @Test
  fun againOnANewCardIsNotALapse() {
    assertEquals(0, Sm2Scheduler.next(CardState(), Grade.AGAIN, 0).lapses)
  }

  @Test
  fun easeNeverDropsBelowTheFloor() {
    var s = CardState()
    repeat(20) { s = Sm2Scheduler.next(s, Grade.AGAIN, 0) }
    assertEquals(Sm2Scheduler.MIN_EASE, s.ease, 1e-9)
  }

  @Test
  fun hardAndEasyBracketGood() {
    val s = CardState(reps = 2, intervalDays = 10.0, ease = 2.5)
    val hard = Sm2Scheduler.next(s, Grade.HARD, 0).intervalDays
    val good = Sm2Scheduler.next(s, Grade.GOOD, 0).intervalDays
    val easy = Sm2Scheduler.next(s, Grade.EASY, 0).intervalDays
    assert(hard < good && good < easy) { "$hard $good $easy" }
  }

  @Test
  fun delayLabels() {
    assertEquals("10m", delayLabel(0, 10 * 60_000))
    assertEquals("5h", delayLabel(0, 5 * 3_600_000))
    assertEquals("3d", delayLabel(0, 3 * day))
  }

  @Test
  fun fixedPolicyUsesTheTunesKey() {
    assertEquals(Key(3, false), concertKeyFor(Key(3, false), KeyPolicy.FIXED, cyclePosition = 5))
  }

  @Test
  fun cyclePolicyWalksTheCircleFromTheTunesKey() {
    val keys = (0..12).map { concertKeyFor(Key(10, true), KeyPolicy.CYCLE, it)!!.displayName }
    assertEquals(
      listOf("Bb minor", "Eb minor", "G# minor", "C# minor", "F# minor", "B minor", "E minor", "A minor", "D minor", "G minor", "C minor", "F minor", "Bb minor"),
      keys,
    )
  }

  @Test
  fun unknownKeyHasNoConcertKeyUnderEitherPolicy() {
    assertNull(concertKeyFor(null, KeyPolicy.FIXED, 0))
    assertNull(concertKeyFor(null, KeyPolicy.CYCLE, 3))
  }

  @Test
  fun exercisePrefersOnesNeverUsedOnTheTune() {
    val picked = (1..50).map { pickExercise(listOf(1L, 2L, 3L), { it }, recentIds = listOf(2L, 1L), Random(it)) }.toSet()
    assertEquals(setOf(3L), picked)
  }

  @Test
  fun exerciseFallsBackToTheLeastRecentlyUsed() {
    assertEquals(1L, pickExercise(listOf(1L, 2L, 3L), { it }, recentIds = listOf(3L, 2L, 1L), Random(0)))
  }
}
