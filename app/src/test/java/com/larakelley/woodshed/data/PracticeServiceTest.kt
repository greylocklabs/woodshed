package com.larakelley.woodshed.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.data.db.ReviewEventEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.seed.Seed
import com.larakelley.woodshed.data.seed.SeedImporter
import com.larakelley.woodshed.domain.CardState
import com.larakelley.woodshed.domain.Comfort
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Focus
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.KeyPolicy
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.PlanBlock
import com.larakelley.woodshed.domain.Sm2Scheduler
import com.larakelley.woodshed.domain.Transposition
import java.time.ZoneOffset
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The practice loop against a real database seeded from the bundled assets. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PracticeServiceTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val standardsJson = context.assets.open("standards.json").bufferedReader().use { it.readText() }
  private val exercisesJson = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
  private lateinit var db: WoodshedDatabase
  private lateinit var practice: PracticeService

  private val day = 24 * 60 * 60 * 1000L
  private var clock = 1_800_000_000_000L - (1_800_000_000_000L % day) + 9 * 60 * 60 * 1000L // 09:00 UTC

  @Before
  fun setUp() = runTest {
    db = Room.inMemoryDatabaseBuilder(context, WoodshedDatabase::class.java).allowMainThreadQueries().build()
    SeedImporter(db).import(Seed.parse(standardsJson, exercisesJson))
    practice = PracticeService(db, now = { clock }, zone = ZoneOffset.UTC, random = Random(1))
  }

  @After
  fun tearDown() = db.close()

  private suspend fun id(slug: String) = db.standards().bySlug(slug)!!.id

  private suspend fun state(slug: String) = db.tuneStates().get(id(slug))

  @Test
  fun planPairsEachChosenTuneWithATuneExercise() = runTest {
    val tunes = listOf(id("autumn-leaves"), id("solar"))
    val plan = practice.plan(45, setOf(Focus.ARTICULATION), tunes, Family.SAXOPHONE)
    assertEquals(45, plan.sumOf { it.minutes })
    val tuneBlocks = plan.filterIsInstance<PlanBlock.Tune>()
    assertEquals(tunes, tuneBlocks.map { it.tuneId })
    val byId = db.exercises().all().associateBy { it.id }
    assertTrue(tuneBlocks.all { byId.getValue(it.exerciseId).appliesToTune })
    val drills = plan.filterIsInstance<PlanBlock.Exercise>().map { byId.getValue(it.exerciseId) }
    assertTrue(drills.all { it.families.isEmpty() || Family.SAXOPHONE in it.families })
    assertTrue(drills.drop(1).all { it.category == "Articulation" })
  }

  @Test
  fun aWishlistTuneInAPlanStartsLearningWhenItComesUp() = runTest {
    practice.addToWishlist(id("solar"))
    val block = practice.plan(30, emptySet(), listOf(id("solar")), null).filterIsInstance<PlanBlock.Tune>().single()
    val card = practice.cardForBlock(block, Transposition.Eb)!!
    assertEquals(Lifecycle.LEARNING, state("solar")!!.lifecycle)
    assertEquals(block.exerciseId, card.exercise.id)
    practice.grade(card, Grade.GOOD)
    assertEquals(1, db.reviewEvents().forItem(ReviewEventEntity.TUNE, id("solar")).size)
  }

  @Test
  fun libraryAndWishlistTunesAreNeverDue() = runTest {
    practice.addToWishlist(id("solar"))
    assertNull(practice.nextCard(Transposition.Bb))
  }

  @Test
  fun cardComposesTuneExerciseAndWrittenKey() = runTest {
    practice.startLearning(id("autumn-leaves"))
    val card = practice.nextCard(Transposition.Bb)!!
    assertEquals("Autumn Leaves", card.tune.title)
    assertTrue(card.exercise.appliesToTune)
    assertEquals("G minor", card.concertKey!!.displayName)
    assertEquals("A minor", card.writtenKey!!.displayName)
  }

  @Test
  fun gradingLogsTheSchedulersInputsAndOutputs() = runTest {
    practice.startLearning(id("autumn-leaves"))
    val card = practice.nextCard(Transposition.Eb)!!
    clock += 90_000
    practice.grade(card, Grade.GOOD)

    val event = db.reviewEvents().forItem(ReviewEventEntity.TUNE, card.tune.id).single()
    assertEquals(Grade.GOOD, event.grade)
    assertEquals(card.exercise.id, event.exerciseId)
    assertEquals("Eb", event.transposition)
    assertEquals(card.writtenKey!!.pitchClass, event.keyWrittenPitchClass)
    assertEquals(90_000, event.elapsedMs)
    assertEquals(0.0, event.intervalBefore, 0.0)
    assertEquals(1.0, event.intervalAfter, 0.0)
    assertEquals("sm2", event.schedulerId)

    assertEquals(clock + day, state("autumn-leaves")!!.dueAt)
    assertNull(practice.nextCard(Transposition.Eb))
  }

  @Test
  fun exercisesRotateBeforeAnyRepeats() = runTest {
    practice.startLearning(id("autumn-leaves"))
    val tuneExercises = db.exercises().all().count { it.appliesToTune }
    val seen = mutableListOf<Long>()
    repeat(tuneExercises) {
      val card = practice.nextCard(Transposition.C)!!
      seen += card.exercise.id
      practice.grade(card, Grade.AGAIN)
      clock += Sm2Scheduler.AGAIN_DELAY_MS
    }
    assertEquals(tuneExercises, seen.toSet().size)
  }

  @Test
  fun goodRepsPromoteToRepertoireAndAgainDemotes() = runTest {
    practice.startLearning(id("solar"))
    while (state("solar")!!.lifecycle == Lifecycle.LEARNING) {
      practice.grade(practice.nextCard(Transposition.C)!!, Grade.GOOD)
      clock = state("solar")!!.dueAt!!
    }
    assertEquals(Lifecycle.REPERTOIRE, state("solar")!!.lifecycle)
    assertTrue(state("solar")!!.intervalDays >= Sm2Scheduler.REPERTOIRE_INTERVAL_DAYS)

    practice.grade(practice.nextCard(Transposition.C)!!, Grade.AGAIN)
    assertEquals(Lifecycle.LEARNING, state("solar")!!.lifecycle)
    assertEquals(1, state("solar")!!.lapses)
  }

  @Test
  fun cyclingKeysAdvancesRoundTheCircleEachReview() = runTest {
    practice.startLearning(id("blue-bossa")) // C minor
    practice.setKeyPolicy(id("blue-bossa"), KeyPolicy.CYCLE)
    val keys = (1..3).map {
      val card = practice.nextCard(Transposition.C)!!
      practice.grade(card, Grade.AGAIN)
      clock += Sm2Scheduler.AGAIN_DELAY_MS
      card.concertKey!!.displayName
    }
    assertEquals(listOf("C minor", "F minor", "Bb minor"), keys)
  }

  @Test
  fun unknownKeyStillMakesACard() = runTest {
    practice.startLearning(id("think-on-me"))
    val card = practice.nextCard(Transposition.Bb)!!
    assertNull(card.writtenKey)
    practice.grade(card, Grade.GOOD)
    assertNull(db.reviewEvents().forItem(ReviewEventEntity.TUNE, card.tune.id).single().keyWrittenPitchClass)
  }

  @Test
  fun replayingTheEventLogRebuildsTheCachedState() = runTest {
    practice.startLearning(id("solar"))
    for (grade in listOf(Grade.GOOD, Grade.GOOD, Grade.HARD, Grade.AGAIN, Grade.GOOD, Grade.EASY)) {
      practice.grade(practice.nextCard(Transposition.C)!!, grade)
      clock = state("solar")!!.dueAt!!
    }
    val events = db.reviewEvents().forItem(ReviewEventEntity.TUNE, id("solar"))
    val replayed = events.fold(CardState()) { s, e -> Sm2Scheduler.next(s, e.grade, e.reviewedAt) }
    val cached = state("solar")!!.cardState
    assertEquals(cached, replayed)
    events.zipWithNext().forEach { (a, b) -> assertEquals(a.intervalAfter, b.intervalBefore, 0.0) }
  }

  @Test
  fun reseedingLeavesPracticeStateAndHistoryAlone() = runTest {
    practice.startLearning(id("solar"))
    practice.grade(practice.nextCard(Transposition.C)!!, Grade.GOOD)
    val stateBefore = state("solar")
    val eventsBefore = db.reviewEvents().forItem(ReviewEventEntity.TUNE, id("solar"))

    val rows = Json.parseToJsonElement(standardsJson).jsonArray.map { row ->
      val o = row.jsonObject
      if (o["slug"] == JsonPrimitive("solar")) JsonObject(o + ("title" to JsonPrimitive("Solar (retitled)"))) else o
    }
    SeedImporter(db).import(Seed.parse(JsonArray(rows).toString(), exercisesJson))

    assertEquals("Solar (retitled)", db.standards().bySlug("solar")!!.title)
    assertEquals(stateBefore, state("solar"))
    assertEquals(eventsBefore, db.reviewEvents().forItem(ReviewEventEntity.TUNE, id("solar")))
  }

  @Test
  fun knownTunesGoStraightToRepertoireAndAreDueNow() = runTest {
    practice.markKnown(id("blue-bossa"))
    assertEquals(Lifecycle.REPERTOIRE, state("blue-bossa")!!.lifecycle)
    assertNotNull(practice.nextCard(Transposition.C))
  }

  @Test
  fun removingATuneKeepsHistoryAndKnowledge() = runTest {
    val solar = id("solar")
    practice.startLearning(solar)
    practice.grade(practice.nextCard(Transposition.C)!!, Grade.GOOD)
    practice.setComfort(solar, Comfort.SHAKY)
    practice.toggleKnownKey(solar, 0)

    practice.removeFromMyTunes(solar)

    assertNull(state("solar"))
    assertEquals(1, db.reviewEvents().forItem(ReviewEventEntity.TUNE, solar).size)
    assertEquals(Comfort.SHAKY, db.tuneKnowledge().get(solar)!!.comfort)
    assertEquals(1, practice.knownKeys(solar))
  }

  @Test
  fun knownKeysToggleInConcertPitch() = runTest {
    val solar = id("solar")
    practice.toggleKnownKey(solar, 0)
    practice.toggleKnownKey(solar, 5)
    practice.toggleKnownKey(solar, 0)
    assertEquals(1 shl 5, practice.knownKeys(solar))
    assertNull(state("solar"))
  }

  @Test
  fun rolledTuneInTheQueueCanBeGraded() = runTest {
    practice.startLearning(id("solar"))
    val roll = practice.roll(DiceConstraints(pool = TunePool.IN_PRACTICE, keyChoice = KeyChoice.ANY))!!
    val card = practice.cardFor(roll, Transposition.Bb)!!
    practice.grade(card, Grade.GOOD)
    val event = db.reviewEvents().forItem(ReviewEventEntity.TUNE, id("solar")).single()
    assertEquals(roll.concertKey!!.writtenFor(Transposition.Bb).pitchClass, event.keyWrittenPitchClass)
  }

  @Test
  fun rolledTuneOutsideTheQueueCannotBeGraded() = runTest {
    practice.addToWishlist(id("solar"))
    val roll = practice.roll(DiceConstraints(pool = TunePool.WISHLIST))!!
    assertNull(practice.cardFor(roll, Transposition.C))
  }

  @Test
  fun retiredTunesLeaveTheQueueAndCanComeBack() = runTest {
    practice.startLearning(id("solar"))
    practice.retire(id("solar"))
    assertNull(practice.nextCard(Transposition.C))
    practice.restore(id("solar"))
    assertNotNull(practice.nextCard(Transposition.C))
  }
}
