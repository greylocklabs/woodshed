package com.larakelley.woodshed.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.data.db.BlockOutcome
import com.larakelley.woodshed.data.db.ReviewEventEntity
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.seed.Seed
import com.larakelley.woodshed.data.seed.SeedImporter
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.PlanBlock
import com.larakelley.woodshed.domain.Transposition
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HistoryServiceTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private lateinit var db: WoodshedDatabase
  private lateinit var log: SessionLog
  private lateinit var history: HistoryService
  private val day = 24 * 60 * 60 * 1000L

  @Before
  fun setUp() = runTest {
    db = Room.inMemoryDatabaseBuilder(context, WoodshedDatabase::class.java).allowMainThreadQueries().build()
    val read = { name: String -> context.assets.open(name).bufferedReader().use { it.readText() } }
    SeedImporter(db).import(Seed.parse(read("standards.json"), read("exercises.json"), read("transcriptions.json")))
    log = SessionLog(db)
    history = HistoryService(db)
  }

  @After
  fun tearDown() = db.close()

  @Test
  fun sessionsComeBackNewestFirstWithTheirBlocksNamed() = runTest {
    val longTones = db.exercises().all().first { it.name == "Long tones" }
    val tuneExercise = db.exercises().all().first { it.appliesToTune }
    val solar = db.standards().bySlug("solar")!!.id
    val first = log.start(listOf(PlanBlock.Exercise(longTones.id, 5)), null, at = 1_000)
    log.finish(first, 0, BlockOutcome.DONE, null, 240_000, at = 241_000)
    log.end(first, at = 242_000, completed = true)
    val second = log.start(listOf(PlanBlock.Exercise(longTones.id, 5), PlanBlock.Tune(solar, tuneExercise.id, 10)), "ALTO_SAX", at = day)
    log.finish(second, 1, BlockOutcome.GRADED, Grade.HARD, 600_000, at = day + 600_000)
    log.end(second, at = day + 700_000, completed = false)

    val sessions = history.sessions()
    assertEquals(listOf(second, first), sessions.map { it.id })
    val blocks = sessions.first().blocks
    assertEquals(listOf("Long tones", "Solar"), blocks.map { it.title })
    assertEquals(listOf("Tone", tuneExercise.name), blocks.map { it.detail })
    assertEquals(listOf(null, BlockOutcome.GRADED), blocks.map { it.outcome })
    assertEquals(600_000L, sessions.first().practicedMs)
  }

  @Test
  fun daysAddUpSessionTimeAndReviews() = runTest {
    val longTones = db.exercises().all().first { it.name == "Long tones" }
    val s = log.start(listOf(PlanBlock.Exercise(longTones.id, 5)), null, at = 2 * day + 1_000)
    log.finish(s, 0, BlockOutcome.DONE, null, 300_000, at = 2 * day + 301_000)
    val practice = PracticeService(db, now = { 3 * day + 5_000 })
    val solar = db.standards().bySlug("solar")!!.id
    practice.startLearning(solar)
    val tuneExercise = db.exercises().all().first { it.appliesToTune }
    practice.grade(practice.cardForBlock(PlanBlock.Tune(solar, tuneExercise.id, 10), Transposition.C)!!, Grade.GOOD)

    val days = history.days(history.sessions(), ZoneOffset.UTC)
    assertEquals(PracticeDay(LocalDate.of(1970, 1, 3), 300_000, 0, 1), days[LocalDate.of(1970, 1, 3)])
    assertEquals(PracticeDay(LocalDate.of(1970, 1, 4), 0, 1), days[LocalDate.of(1970, 1, 4)])
    assertEquals(2, days.size)
  }

  @Test
  fun statsCountStreaksTunesAndKeys() = runTest {
    val longTones = db.exercises().all().first { it.name == "Long tones" }
    val tuneExercise = db.exercises().all().first { it.appliesToTune }
    // Sessions on days 1, 2 and 3, then a gap, then day 5.
    for (d in listOf(1, 2, 3, 5)) {
      val s = log.start(listOf(PlanBlock.Exercise(longTones.id, 5)), null, at = d * day + 1_000)
      log.finish(s, 0, BlockOutcome.DONE, null, 300_000, at = d * day + 301_000)
    }
    var clock = 5 * day
    val practice = PracticeService(db, now = { clock })
    val solar = db.standards().bySlug("solar")!!.id
    val nardis = db.standards().bySlug("nardis")!!.id
    practice.startLearning(solar)
    practice.startLearning(nardis)
    repeat(2) { practice.grade(practice.cardForBlock(PlanBlock.Tune(solar, tuneExercise.id, 10), Transposition.C)!!, Grade.GOOD) }
    practice.grade(practice.cardForBlock(PlanBlock.Tune(nardis, tuneExercise.id, 10), Transposition.C)!!, Grade.GOOD)

    val sessions = history.sessions()
    val stats = history.stats(sessions, ZoneOffset.UTC, today = LocalDate.of(1970, 1, 6))
    assertEquals(4 * 300_000L, stats.practicedMs)
    assertEquals(4, stats.sessions)
    // Today has no practice yet, so the streak runs to yesterday.
    assertEquals(1, stats.currentStreak)
    assertEquals(3, stats.longestStreak)
    assertEquals(listOf("Solar" to 2, "Nardis" to 1), stats.topTunes)
    assertEquals(3, stats.repsByKey.sum())
    assertEquals(2, stats.learning)
    assertEquals(0, stats.repertoire)
    // A week with no practice breaks the streak.
    assertEquals(0, history.stats(sessions, ZoneOffset.UTC, today = LocalDate.of(1970, 1, 12)).currentStreak)
  }

  @Test
  fun statsCountTunesReachingRepertoireThisMonth() = runTest {
    val solar = db.standards().bySlug("solar")!!.id
    val nardis = db.standards().bySlug("nardis")!!.id
    db.tuneStates().upsert(TuneStateEntity(solar, Lifecycle.REPERTOIRE))
    db.tuneStates().upsert(TuneStateEntity(nardis, Lifecycle.LEARNING))
    val oct1 = LocalDate.of(2026, 10, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun crossing(id: Long, at: Long, before: Double, after: Double) = ReviewEventEntity(
      itemKind = ReviewEventEntity.TUNE, itemId = id, exerciseId = null, transposition = "C", keyWrittenPitchClass = 0, keyWrittenMinor = false,
      grade = Grade.GOOD, reviewedAt = at, elapsedMs = 0, schedulerId = "sm2", schedulerVersion = 1, intervalBefore = before, intervalAfter = after,
      easeBefore = 2.5, easeAfter = 2.5,
    )
    // Solar reached 21 days this month, Nardis last month; a review already in repertoire does not count again.
    db.reviewEvents().insert(crossing(solar, oct1 + day, 10.0, 25.0))
    db.reviewEvents().insert(crossing(solar, oct1 + 5 * day, 25.0, 60.0))
    db.reviewEvents().insert(crossing(nardis, oct1 - day, 10.0, 25.0))

    val stats = history.stats(emptyList(), ZoneOffset.UTC, today = LocalDate.of(2026, 10, 16))
    assertEquals(1, stats.repertoire)
    assertEquals(1, stats.learning)
    assertEquals(1, stats.reachedRepertoireThisMonth)
  }

  @Test
  fun deletingASessionTakesItsBlocksAndLeavesTheOthers() = runTest {
    val longTones = db.exercises().all().first { it.name == "Long tones" }
    val keep = log.start(listOf(PlanBlock.Exercise(longTones.id, 5)), null, at = 1_000)
    val drop = log.start(listOf(PlanBlock.Exercise(longTones.id, 5), PlanBlock.Exercise(longTones.id, 5)), null, at = 2_000)
    history.deleteSession(drop)
    assertEquals(listOf(keep), history.sessions().map { it.id })
    assertEquals(listOf(keep), db.sessions().allBlocks().map { it.sessionId })
  }
}
