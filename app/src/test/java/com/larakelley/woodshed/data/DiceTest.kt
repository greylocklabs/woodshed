package com.larakelley.woodshed.data

import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.TranscriptionEntity
import com.larakelley.woodshed.data.db.TranscriptionStateEntity
import com.larakelley.woodshed.data.db.TranscriptionStatus
import com.larakelley.woodshed.data.db.TuneKnowledgeEntity
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.KeyMask
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.Tonality
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceTest {
  private fun tune(id: Long, key: String?, tonality: Tonality?, style: String = "Swing") =
    StandardEntity(id, "t$id", "Tune $id", null, null, 32, "4/4", "AABA", style, key, Key.pitchClassOf(key), tonality)

  private val tunes = listOf(tune(1, "F", Tonality.MAJOR), tune(2, "C", Tonality.MINOR, "Ballad"), tune(3, null, null), tune(4, "Bb", Tonality.MAJOR))
  private val states = mapOf(
    1L to TuneStateEntity(1, Lifecycle.LEARNING),
    2L to TuneStateEntity(2, Lifecycle.REPERTOIRE),
    3L to TuneStateEntity(3, Lifecycle.WISHLIST),
    4L to TuneStateEntity(4, Lifecycle.RETIRED),
  )
  private val exercises = listOf(
    ExerciseEntity(1, "Melody from memory", "Standards", "", 10, 3, null, emptyList(), true),
    ExerciseEntity(2, "Guide-tone solo", "Improvisation", "", 8, 4, null, emptyList(), true),
    ExerciseEntity(3, "Long tones", "Tone", "", 10, 2, 60, emptyList(), false),
  )

  private fun rolls(c: DiceConstraints, knowledge: Map<Long, TuneKnowledgeEntity> = emptyMap()) =
    (1..200).mapNotNull { roll(tunes, states, knowledge, exercises, c, Random(it)) }

  @Test
  fun poolsSelectByLifecycle() {
    assertEquals(setOf(1L, 2L, 3L), rolls(DiceConstraints(pool = TunePool.MY_TUNES)).map { it.tune.id }.toSet())
    assertEquals(setOf(1L, 2L), rolls(DiceConstraints(pool = TunePool.IN_PRACTICE)).map { it.tune.id }.toSet())
    assertEquals(setOf(3L), rolls(DiceConstraints(pool = TunePool.WISHLIST)).map { it.tune.id }.toSet())
    assertEquals(setOf(1L, 2L, 3L, 4L), rolls(DiceConstraints(pool = TunePool.ANY)).map { it.tune.id }.toSet())
  }

  @Test
  fun onlyTuneExercisesAreDrawn() {
    assertEquals(setOf("Melody from memory", "Guide-tone solo"), rolls(DiceConstraints()).map { it.exercise.name }.toSet())
    assertEquals(setOf("Guide-tone solo"), rolls(DiceConstraints(exerciseName = "Guide-tone solo")).map { it.exercise.name }.toSet())
  }

  @Test
  fun styleNarrowsThePool() {
    assertEquals(setOf(2L), rolls(DiceConstraints(style = "Ballad")).map { it.tune.id }.toSet())
  }

  @Test
  fun usualKeyIsTheTunesKeyOrNullWhenUnknown() {
    for (r in rolls(DiceConstraints())) {
      when (r.tune.id) {
        1L -> assertEquals("F", r.concertKey!!.displayName)
        2L -> assertEquals("C minor", r.concertKey!!.displayName)
        3L -> assertNull(r.concertKey)
      }
    }
  }

  @Test
  fun anyKeyKeepsTheTunesMode() {
    val minorRolls = rolls(DiceConstraints(style = "Ballad", keyChoice = KeyChoice.ANY))
    assertTrue(minorRolls.all { it.concertKey!!.minor })
    assertTrue(minorRolls.map { it.concertKey!!.pitchClass }.toSet().size > 6)
  }

  @Test
  fun notYetKnownNeverPicksACheckedKey() {
    val known = (0..10).fold(0) { m, pc -> KeyMask.toggle(m, pc) } // everything but B
    val r = rolls(DiceConstraints(pool = TunePool.IN_PRACTICE, keyChoice = KeyChoice.NOT_YET_KNOWN), mapOf(1L to TuneKnowledgeEntity(1, knownKeys = known), 2L to TuneKnowledgeEntity(2, knownKeys = known)))
    assertEquals(setOf(11), r.map { it.concertKey!!.pitchClass }.toSet())
  }

  @Test
  fun collectionNarrowsThePool() {
    val trad = tunes.map { if (it.id == 2L) it.copy(collection = "Trad") else it }
    val ids = (1..100).mapNotNull { roll(trad, states, emptyMap(), exercises, DiceConstraints(collection = "Trad"), Random(it)) }.map { it.tune.id }.toSet()
    assertEquals(setOf(2L), ids)
  }

  @Test
  fun solosRollFromTheChosenPart() {
    fun solo(id: Long) = TranscriptionEntity(id = id, slug = "s$id", title = "Solo $id", soloist = "X", instrument = "Trumpet", url = "https://example.org")
    val solos = listOf(solo(1), solo(2), solo(3), solo(4).copy(archivedAt = 1))
    val soloStates = mapOf(1L to TranscriptionStateEntity(1, TranscriptionStatus.ACTIVE), 2L to TranscriptionStateEntity(2, TranscriptionStatus.WISHLIST), 4L to TranscriptionStateEntity(4, TranscriptionStatus.ACTIVE))
    fun drawn(pool: SoloPool) = (1..100).mapNotNull { rollSolo(solos, soloStates, pool, Random(it))?.id }.toSet()
    assertEquals(setOf(1L, 2L), drawn(SoloPool.MINE))
    assertEquals(setOf(1L), drawn(SoloPool.TRANSCRIBING))
    assertEquals(setOf(2L), drawn(SoloPool.WISHLIST))
    assertEquals(setOf(1L, 2L, 3L), drawn(SoloPool.ANY))
    assertNull(rollSolo(solos.take(0), soloStates, SoloPool.ANY, Random(0)))
  }

  @Test
  fun nothingMatchesGivesNull() {
    assertNull(roll(tunes, states, emptyMap(), exercises, DiceConstraints(pool = TunePool.WISHLIST, style = "Ballad"), Random(0)))
  }
}
