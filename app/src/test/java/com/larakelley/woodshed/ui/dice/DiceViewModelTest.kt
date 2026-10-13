package com.larakelley.woodshed.ui.dice

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.data.PracticeService
import com.larakelley.woodshed.data.RollKind
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.data.db.ReviewEventEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.seed.Seed
import com.larakelley.woodshed.data.seed.SeedImporter
import com.larakelley.woodshed.domain.Grade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DiceViewModelTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private lateinit var db: WoodshedDatabase
  private lateinit var practice: PracticeService
  private lateinit var dice: DiceViewModel

  @Before
  fun setUp() = runTest {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    // Queries run in place, so each view model call has finished by the time it returns.
    db = Room.inMemoryDatabaseBuilder(context, WoodshedDatabase::class.java)
      .allowMainThreadQueries()
      .setQueryExecutor { it.run() }
      .setTransactionExecutor { it.run() }
      .build()
    val read = { name: String -> context.assets.open(name).bufferedReader().use { it.readText() } }
    SeedImporter(db).import(Seed.parse(read("standards.json"), read("exercises.json"), read("transcriptions.json")))
    val repository = WoodshedRepository(db, context.getSharedPreferences("dice-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }, context.assets)
    practice = PracticeService(db, random = Random(1))
    practice.startLearning(db.standards().bySlug("solar")!!.id)
    dice = DiceViewModel(repository, practice, now = { 1_000L })
  }

  @After
  fun tearDown() {
    db.close()
    Dispatchers.resetMain()
  }

  @Test
  fun switchingBetweenTunesAndSolosClearsTheLastRoll() {
    dice.roll()
    assertNotNull(dice.ui.value.card)
    dice.constrain { it.copy(kind = RollKind.SOLO) }
    assertNull(dice.ui.value.card)
    assertNull(dice.ui.value.solo)
    assertFalse(dice.ui.value.nothingMatches)
  }

  @Test
  fun aSecondTapOnAGradeIsIgnored() = runTest {
    dice.roll()
    dice.grade(Grade.GOOD)
    dice.grade(Grade.GOOD)
    assertEquals(1, db.reviewEvents().all().count { it.itemKind == ReviewEventEntity.TUNE })
  }
}
