package com.larakelley.woodshed.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.data.db.BlockOutcome
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.seed.Seed
import com.larakelley.woodshed.data.seed.SeedImporter
import com.larakelley.woodshed.domain.Grade
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
    assertEquals(PracticeDay(LocalDate.of(1970, 1, 3), 300_000, 0), days[LocalDate.of(1970, 1, 3)])
    assertEquals(PracticeDay(LocalDate.of(1970, 1, 4), 0, 1), days[LocalDate.of(1970, 1, 4)])
    assertEquals(2, days.size)
  }
}
