package com.larakelley.woodshed.data

import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.TranscriptionEntity
import com.larakelley.woodshed.data.db.TranscriptionStateEntity
import com.larakelley.woodshed.data.db.TranscriptionStatus
import com.larakelley.woodshed.data.db.TuneKnowledgeEntity
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.domain.ChangesLibrary
import com.larakelley.woodshed.domain.ChangesSection
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Instrument
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.KeyMask
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.Tonality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

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
    val r = rolls(
      DiceConstraints(pool = TunePool.IN_PRACTICE, keyChoice = KeyChoice.NOT_YET_KNOWN),
      mapOf(
        1L to TuneKnowledgeEntity(1, knownKeys = known),
        2L to TuneKnowledgeEntity(2, knownKeys = known),
      ),
    )
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
    val soloStates = mapOf(
      1L to TranscriptionStateEntity(1, TranscriptionStatus.ACTIVE),
      2L to TranscriptionStateEntity(2, TranscriptionStatus.WISHLIST),
      4L to TranscriptionStateEntity(4, TranscriptionStatus.ACTIVE),
    )
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

  @Test
  fun anExerciseRollSuitsTheInstrumentAndTheLimits() {
    val pool = exercises + listOf(
      ExerciseEntity(4, "Overtones", "Tone", "", 10, 4, null, listOf(Family.SAXOPHONE), false),
      ExerciseEntity(5, "Lip slurs", "Tone", "", 10, 3, null, listOf(Family.BRASS), false),
    )
    fun names(instrument: Instrument?, c: DiceConstraints = DiceConstraints(kind = RollKind.EXERCISE)) =
      (1..200).mapNotNull { rollExercise(pool, instrument, c, Random(it))?.name }.toSet()

    assertEquals(setOf("Melody from memory", "Guide-tone solo", "Long tones", "Overtones"), names(Instrument.TENOR_SAX))
    assertEquals(setOf("Long tones", "Overtones"), names(Instrument.ALTO_SAX, DiceConstraints(kind = RollKind.EXERCISE, exerciseCategory = "Tone")))
    assertEquals(setOf("Overtones"), names(Instrument.ALTO_SAX, DiceConstraints(kind = RollKind.EXERCISE, exerciseCategory = "Tone", exerciseDifficulty = 4)))
    assertNull(rollExercise(pool, Instrument.TRUMPET, DiceConstraints(exerciseDifficulty = 5), Random(0)))
  }

  @Test
  fun changesComeFromTheChosenPool() {
    val mine = listOf(7L to "Bridge to F")
    fun routes(pool: ChangesPool) = (1..200).mapNotNull { rollChanges(mine, pool, Random(it))?.route }.toSet()
    assertTrue(routes(ChangesPool.BLUES).all { ChangesLibrary.form(it.removePrefix("form:"))?.section == ChangesSection.BLUES })
    assertTrue(routes(ChangesPool.PATTERNS).all { it.startsWith("pattern:") })
    // Every section can be rolled on its own.
    for (section in ChangesSection.entries) assertTrue(ChangesPool.entries.any { it.section == section })
    assertTrue(
      routes(ChangesPool.MORE).isNotEmpty() && routes(ChangesPool.MORE).all {
        ChangesLibrary.form(it.removePrefix("form:"))?.section == ChangesSection.MORE
      },
    )
    assertEquals(setOf("mine:7"), routes(ChangesPool.MINE))
    assertTrue(routes(ChangesPool.ANY).let { r -> "mine:7" in r && r.any { it.startsWith("form:") } && r.any { it.startsWith("pattern:") } })
    assertNull(rollChanges(emptyList(), ChangesPool.MINE, Random(1)))
  }
}
