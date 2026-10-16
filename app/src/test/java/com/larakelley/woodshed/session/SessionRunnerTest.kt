package com.larakelley.woodshed.session

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.data.PracticeService
import com.larakelley.woodshed.data.SessionLog
import com.larakelley.woodshed.data.TranscriptionService
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.data.db.BlockOutcome
import com.larakelley.woodshed.data.db.ReviewEventEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.seed.Seed
import com.larakelley.woodshed.data.seed.SeedImporter
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.PlanBlock
import com.larakelley.woodshed.ui.practice.StepView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SessionRunnerTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private lateinit var db: WoodshedDatabase
  private lateinit var repository: WoodshedRepository
  private lateinit var practice: PracticeService
  private lateinit var store: SessionStore
  private var clock = 1_000_000L
  private lateinit var plan: List<PlanBlock>

  @Before
  fun setUp() = runTest {
    // Queries run in place and the runner's scope is unconfined, so each call has finished when it returns.
    db = Room.inMemoryDatabaseBuilder(context, WoodshedDatabase::class.java)
      .allowMainThreadQueries()
      .setQueryExecutor { it.run() }
      .setTransactionExecutor { it.run() }
      .build()
    val read = { name: String -> context.assets.open(name).bufferedReader().use { it.readText() } }
    SeedImporter(db).import(Seed.parse(read("standards.json"), read("exercises.json"), read("transcriptions.json")))
    repository =
      WoodshedRepository(db, context.getSharedPreferences("runner-settings", Context.MODE_PRIVATE).also { it.edit().clear().commit() }, context.assets)
    practice = PracticeService(db, random = Random(1))
    store = SessionStore(context.getSharedPreferences("runner-session", Context.MODE_PRIVATE).also { it.edit().clear().commit() })
    val exercises = db.exercises().all()
    val drill = exercises.first { !it.appliesToTune }
    val tuneExercise = exercises.first { it.appliesToTune }
    plan = listOf(PlanBlock.Exercise(drill.id, 5), PlanBlock.Tune(db.standards().bySlug("solar")!!.id, tuneExercise.id, 10))
  }

  @After
  fun tearDown() = db.close()

  private fun runner() = SessionRunner(
    repository,
    practice,
    TranscriptionService(db),
    store,
    SessionLog(db),
    CoroutineScope(UnconfinedTestDispatcher()),
    now = { clock },
  )

  @Test
  fun aSessionStepsThroughItsBlocksAndFinishes() {
    val r = runner()
    r.start(plan)
    assertTrue(r.state.value.step is StepView.Exercise)
    r.next()
    assertTrue(r.state.value.step is StepView.Tune)
    r.grade(Grade.GOOD)
    // A second tap on a grade has nothing left to grade.
    r.grade(Grade.GOOD)
    assertEquals(1, runTestResult { db.reviewEvents().all().count { it.itemKind == ReviewEventEntity.TUNE } })
    assertTrue(r.state.value.running)
    assertNull(r.state.value.step)
    assertNull(store.load())
  }

  @Test
  fun aSessionIsRecordedWithHowEachBlockWent() {
    val r = runner()
    r.start(plan)
    clock += 4 * 60_000
    r.next()
    clock += 60_000
    r.pause()
    clock += 10 * 60_000
    r.resume()
    clock += 2 * 60_000
    r.grade(Grade.GOOD)
    // Complete as soon as the last block is done, before Finish is tapped or even if it never is.
    val doneAt = clock
    val completed = runTestResult { db.sessions().all() }.single()
    assertTrue(completed.completed)
    assertEquals(doneAt, completed.endedAt)
    clock += 60_000
    r.stop()

    val session = runTestResult { db.sessions().all() }.single()
    assertEquals(15, session.plannedMinutes)
    assertTrue(session.completed)
    assertEquals("Finish leaves the end where the last block finished", doneAt, session.endedAt)
    val blocks = runTestResult { db.sessions().allBlocks() }
    assertEquals(listOf(BlockOutcome.DONE, BlockOutcome.GRADED), blocks.map { it.outcome })
    assertEquals(Grade.GOOD, blocks[1].grade)
    // The pause is left out of the time spent.
    assertEquals(listOf(4 * 60_000L, 3 * 60_000L), blocks.map { it.elapsedMs })
  }

  @Test
  fun goingBackFromDoneAndEndingKeepsTheSessionCompleted() {
    val r = runner()
    r.start(plan)
    r.next()
    r.grade(Grade.GOOD)
    val doneAt = clock
    clock += 60_000
    r.previous()
    r.stop()
    val session = runTestResult { db.sessions().all() }.single()
    assertTrue(session.completed)
    assertEquals(doneAt, session.endedAt)
  }

  @Test
  fun endingEarlyLeavesTheRestUnfinished() {
    val r = runner()
    r.start(plan)
    r.next()
    r.stop()
    val session = runTestResult { db.sessions().all() }.single()
    assertFalse(session.completed)
    assertEquals(listOf(BlockOutcome.DONE, null), runTestResult { db.sessions().allBlocks() }.map { it.outcome })
  }

  @Test
  fun aStepCanGoBackToThePreviousBlock() {
    val r = runner()
    r.start(plan)
    // Nothing comes before the first block.
    r.previous()
    assertEquals(0, r.state.value.stepIndex)
    r.next()
    clock += 60_000
    r.previous()
    assertEquals(0, r.state.value.stepIndex)
    assertTrue(r.state.value.step is StepView.Exercise)
    assertEquals(clock, r.state.value.stepStartedAt)
    assertEquals(0, store.load()!!.index)
    // From the end, back to the last block.
    r.next()
    r.next()
    assertNull(r.state.value.step)
    r.previous()
    assertTrue(r.state.value.step is StepView.Tune)
  }

  @Test
  fun pausingStopsTheBlockClock() {
    val r = runner()
    r.start(plan)
    val started = r.state.value.stepStartedAt
    clock += 60_000
    r.pause()
    assertEquals(clock, r.state.value.pausedAt)
    clock += 5 * 60_000
    r.resume()
    assertNull(r.state.value.pausedAt)
    assertEquals(started + 5 * 60_000, r.state.value.stepStartedAt)
  }

  @Test
  fun aKilledSessionComesBackWhereItWas() {
    val first = runner()
    first.start(plan)
    first.next()
    val startedAt = first.state.value.stepStartedAt
    clock += 2 * 60_000
    first.pause()

    val second = runner()
    second.restore()
    assertTrue(second.ready.isCompleted)
    val s = second.state.value
    assertTrue(s.running)
    assertEquals(1, s.stepIndex)
    assertEquals(startedAt, s.stepStartedAt)
    assertEquals(clock, s.pausedAt)
  }

  @Test
  fun anOldSavedSessionIsDropped() {
    runner().start(plan)
    clock += SessionRunner.STALE_MS
    val r = runner()
    r.restore()
    assertFalse(r.state.value.running)
    assertNull(store.load())
  }

  @Test
  fun endingClearsTheSavedSession() {
    val r = runner()
    r.start(plan)
    r.stop()
    assertFalse(r.state.value.running)
    assertNull(store.load())
  }

  private fun <T> runTestResult(block: suspend () -> T): T {
    var result: T? = null
    runTest { result = block() }
    @Suppress("UNCHECKED_CAST")
    return result as T
  }
}
