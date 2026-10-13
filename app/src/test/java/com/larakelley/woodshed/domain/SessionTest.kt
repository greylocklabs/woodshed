package com.larakelley.woodshed.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
  private var nextId = 0L
  private fun ex(category: String, minutes: Int = 10, tune: Boolean = false, difficulty: Int = 2) = PlanExercise(++nextId, category, minutes, tune, difficulty)

  private val exercises = listOf(
    ex("Tone", 10), ex("Air & Breathing", 5), ex("Tone", 6, difficulty = 5),
    ex("Articulation", 10), ex("Articulation", 8),
    ex("Technique", 10), ex("Scales & Patterns", 15), ex("Licks", 10),
    ex("ii-V-I & Progressions", 10),
    ex("Standards", 10, tune = true), ex("Standards", 8, tune = true),
  )
  private val categories = exercises.associate { it.id to it.category }
  private val tuneExerciseId = exercises.first { it.appliesToTune }.id

  private fun plan(minutes: Int, focus: Set<Focus> = emptySet(), tunes: List<Long> = emptyList(), seed: Int = 0) =
    planSession(minutes, focus, tunes, exercises, { tuneExerciseId }, Random(seed))

  private fun List<PlanBlock>.drillCategories() = filterIsInstance<PlanBlock.Exercise>().map { categories.getValue(it.exerciseId) }

  @Test
  fun fillsTheRequestedTimeWithTunes() {
    for (seed in 0..20) {
      for (minutes in listOf(15, 30, 45, 60)) {
        assertEquals("$minutes min, seed $seed", minutes, plan(minutes, setOf(Focus.SPEED), listOf(1, 2), seed).sumOf { it.minutes })
      }
    }
  }

  @Test
  fun fillsTheRequestedTimeWithoutTunesEvenWhenExercisesRunShort() {
    // Air & Breathing has a single five-minute exercise, so a long session on that focus runs out of drills.
    for (minutes in listOf(10, 15, 60)) {
      assertEquals("$minutes min", minutes, plan(minutes, setOf(Focus.TONE)).sumOf { it.minutes })
    }
  }

  @Test
  fun startsWithAToneWarmup() {
    for (seed in 0..20) {
      val first = plan(30, setOf(Focus.ARTICULATION), seed = seed).first() as PlanBlock.Exercise
      assertTrue(categories.getValue(first.exerciseId) in Focus.TONE.categories)
      assertEquals(5, first.minutes)
    }
  }

  @Test
  fun theWarmupIsNeverAHardExercise() {
    val hard = exercises.filter { it.difficulty > MAX_WARMUP_DIFFICULTY }.map { it.id }.toSet()
    for (seed in 0..40) {
      val first = plan(30, setOf(Focus.SPEED), seed = seed).first() as PlanBlock.Exercise
      assertTrue(first.exerciseId !in hard)
    }
  }

  @Test
  fun exercisesAfterTheWarmupComeFromTheFocus() {
    for (seed in 0..20) {
      val drills = plan(60, setOf(Focus.ARTICULATION, Focus.HARMONY), seed = seed).drillCategories().drop(1)
      assertTrue(drills.toString(), drills.all { it in Focus.ARTICULATION.categories + Focus.HARMONY.categories })
      // Round-robin: both focus areas get a turn before either repeats.
      assertEquals(setOf("Articulation", "ii-V-I & Progressions"), drills.take(2).toSet())
    }
  }

  @Test
  fun tunesComeLastWithAboutHalfTheTimeAndAtLeastFiveMinutesEach() {
    val blocks = plan(60, setOf(Focus.SPEED), listOf(1, 2, 3))
    val tunes = blocks.filterIsInstance<PlanBlock.Tune>()
    assertEquals(listOf(1L, 2L, 3L), tunes.map { it.tuneId })
    assertTrue(blocks.takeLast(3).all { it is PlanBlock.Tune })
    assertTrue(tunes.sumOf { it.minutes } >= (60 - 10) / 2)
    assertTrue(tunes.all { it.minutes >= 5 })
  }

  @Test
  fun tunesThatDoNotFitAreDroppedFromTheEnd() {
    val tunes = plan(15, tunes = (1L..10L).toList()).filterIsInstance<PlanBlock.Tune>()
    assertTrue(tunes.size in 1..2)
    assertEquals((1L..tunes.size).toList(), tunes.map { it.tuneId })
  }

  @Test
  fun noExerciseRepeatsAndTuneExercisesAreNotDrills() {
    for (seed in 0..20) {
      val drills = plan(120, seed = seed).filterIsInstance<PlanBlock.Exercise>().map { it.exerciseId }
      assertEquals(drills.size, drills.toSet().size)
      assertTrue(drills.none { categories.getValue(it) == "Standards" })
    }
  }

  @Test
  fun aFocusWithFewExercisesGivesTheRestToTunes() {
    val blocks = plan(60, setOf(Focus.READING), listOf(1))
    assertEquals(60, blocks.sumOf { it.minutes })
    assertEquals(55, (blocks.last() as PlanBlock.Tune).minutes)
  }
}
