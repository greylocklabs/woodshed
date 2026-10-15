package com.larakelley.woodshed.ui.practice

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.data.PlaylistService
import com.larakelley.woodshed.data.PracticeService
import com.larakelley.woodshed.data.TranscriptionService
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.seed.Seed
import com.larakelley.woodshed.data.seed.SeedImporter
import com.larakelley.woodshed.domain.Instrument
import com.larakelley.woodshed.domain.PlanBlock
import com.larakelley.woodshed.session.SessionRunner
import com.larakelley.woodshed.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

/** Session setup end to end: what starts ticked and chosen, playlists, and planning, starting and ending a session. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PracticeViewModelTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private lateinit var db: WoodshedDatabase
  private lateinit var repository: WoodshedRepository
  private lateinit var practice: PracticeService
  private lateinit var solos: TranscriptionService
  private lateinit var sessions: SessionRunner

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
    repository = WoodshedRepository(db, context.getSharedPreferences("practice-vm", Context.MODE_PRIVATE).also { it.edit().clear().commit() }, context.assets)
    practice = PracticeService(db, random = Random(1))
    solos = TranscriptionService(db)
    val store = SessionStore(context.getSharedPreferences("practice-vm-session", Context.MODE_PRIVATE).also { it.edit().clear().commit() })
    sessions = SessionRunner(repository, practice, solos, store, CoroutineScope(UnconfinedTestDispatcher()))
  }

  @After
  fun tearDown() {
    db.close()
    Dispatchers.resetMain()
  }

  private suspend fun id(slug: String) = db.standards().bySlug(slug)!!.id

  private suspend fun soloId(slug: String) = db.transcriptions().bySlug(slug)!!.id

  @Test
  fun setupTicksTheDueTunesAndChoosesTheSoloBeingTranscribed() = runTest {
    practice.startLearning(id("solar"))
    practice.addToWishlist(id("nardis"))
    solos.start(soloId("tangerine-chet-baker"))
    val vm = PracticeViewModel(repository, practice, sessions)

    val setup = vm.ui.value.setup
    assertEquals(setOf(id("solar")), setup.selected)
    assertTrue(setup.tunes.any { it.id == id("nardis") && it.group == TuneGroup.WISHLIST })
    assertEquals(soloId("tangerine-chet-baker"), setup.soloId)
  }

  @Test
  fun aPlaylistTicksItsTunesUntilTheSelectionChangesByHand() = runTest {
    practice.startLearning(id("solar"))
    val lists = PlaylistService(db)
    val gig = lists.create("Gig")
    lists.add(gig, id("blue-bossa"))
    val vm = PracticeViewModel(repository, practice, sessions)

    vm.usePlaylist(gig)
    assertEquals(setOf(id("blue-bossa")), vm.ui.value.setup.selected)
    assertTrue(vm.ui.value.setup.tunes.any { it.id == id("blue-bossa") && it.group == TuneGroup.PLAYLIST })

    vm.toggleTune(id("solar"))
    assertEquals(setOf(id("blue-bossa"), id("solar")), vm.ui.value.setup.selected)

    // Going back to no playlist goes back to the due tunes.
    vm.usePlaylist(null)
    assertEquals(setOf(id("solar")), vm.ui.value.setup.selected)
  }

  @Test
  fun thePlanFollowsTheChosenSoloAndASessionReturnsToSetupWhenItEnds() = runTest {
    practice.startLearning(id("solar"))
    solos.start(soloId("tangerine-chet-baker"))
    val vm = PracticeViewModel(repository, practice, sessions)
    vm.setInstrument(Instrument.ALTO_SAX)
    vm.setMinutes(30)

    vm.pickSolo(null)
    vm.buildPlan()
    assertTrue(vm.ui.value.setup.plan!!.none { it.block is PlanBlock.Solo })

    vm.pickSolo(soloId("tangerine-chet-baker"))
    vm.buildPlan()
    assertEquals(listOf(soloId("tangerine-chet-baker")), vm.ui.value.setup.plan!!.mapNotNull { (it.block as? PlanBlock.Solo)?.transcriptionId })

    vm.start()
    assertTrue(vm.ui.value.running)
    vm.stop()
    assertFalse(vm.ui.value.running)
    assertNull(vm.ui.value.setup.plan)
  }

  @Test
  fun aPlannedExerciseCanBeReplacedWithAnotherOfItsKindNotAlreadyPlanned() = runTest {
    practice.startLearning(id("solar"))
    val vm = PracticeViewModel(repository, practice, sessions)
    vm.setInstrument(Instrument.ALTO_SAX)
    vm.setMinutes(60)
    vm.buildPlan()
    val plan = vm.ui.value.setup.plan!!
    val index = plan.indexOfFirst { it.block is PlanBlock.Exercise }
    val block = plan[index].block as PlanBlock.Exercise
    val planned = plan.mapNotNull { (it.block as? PlanBlock.Exercise)?.exerciseId }.toSet()

    val options = vm.replacements(index)
    assertTrue(options.isNotEmpty())
    assertTrue(options.all { it.category == plan[index].exercise!!.category && it.id !in planned })

    vm.replace(index, options.first().id)
    val replaced = vm.ui.value.setup.plan!![index]
    assertEquals(options.first().id, (replaced.block as PlanBlock.Exercise).exerciseId)
    assertEquals(block.minutes, replaced.block.minutes)

    // A solo block has nothing to replace.
    vm.pickSolo(null)
    assertTrue(vm.replacements(99).isEmpty())
  }

  @Test
  fun droppingBlocksKeepsThePlanAtTheSessionLength() = runTest {
    practice.startLearning(id("solar"))
    val vm = PracticeViewModel(repository, practice, sessions)
    vm.setInstrument(Instrument.ALTO_SAX)
    vm.setMinutes(30)
    vm.buildPlan()
    assertEquals(30, vm.ui.value.setup.plan!!.sumOf { it.block.minutes })
    vm.removeBlock(1)
    vm.removeBlock(1)
    val plan = vm.ui.value.setup.plan!!
    assertEquals(30, plan.sumOf { it.block.minutes })
  }
}
