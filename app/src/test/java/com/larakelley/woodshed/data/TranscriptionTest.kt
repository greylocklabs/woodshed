package com.larakelley.woodshed.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.data.db.TranscriptionStatus
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.seed.Seed
import com.larakelley.woodshed.data.seed.SeedImporter
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.Instrument
import com.larakelley.woodshed.domain.PlanBlock
import com.larakelley.woodshed.domain.Tonality
import com.larakelley.woodshed.domain.soloMinutes
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TranscriptionTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val read = { name: String -> context.assets.open(name).bufferedReader().use { it.readText() } }
  private lateinit var db: WoodshedDatabase
  private lateinit var service: TranscriptionService
  private var clockMs = 1_000_000L

  @Before
  fun setUp() = runTest {
    db = Room.inMemoryDatabaseBuilder(context, WoodshedDatabase::class.java).allowMainThreadQueries().build()
    SeedImporter(db).import(Seed.parse(read("standards.json"), read("exercises.json"), read("transcriptions.json")))
    service = TranscriptionService(db, now = { clockMs })
  }

  @After
  fun tearDown() = db.close()

  private suspend fun bartley() = db.transcriptions().bySlug("singin-in-the-rain-patrick-bartley")!!

  @Test
  fun theBundledSoloImportsLinkedToItsTune() = runTest {
    val solo = bartley()
    assertEquals(db.standards().bySlug("singin-in-the-rain")!!.id, solo.standardId)
    assertEquals(120, solo.startSeconds)
    assertEquals(329, solo.endSeconds)
    assertEquals(1, solo.keyPitchClass)
    assertEquals(Tonality.MAJOR, solo.tonality)
    assertEquals("2022-08-13", solo.recorded)
    assertEquals(listOf("Emmet Cohen", "Patrick Bartley", "Yasushi Nakamura", "Kyle Poole"), solo.performers.map { it.name })
  }

  @Test
  fun startingFinishingAndGradingTrackProgress() = runTest {
    val id = bartley().id
    service.start(id)
    assertEquals(TranscriptionStatus.ACTIVE, db.transcriptions().state(id)!!.status)
    service.grade(id, Grade.GOOD)
    val graded = db.transcriptions().state(id)!!
    assertEquals(1, graded.reps)
    assertTrue(graded.dueAt!! > clockMs)
    assertEquals("TRANSCRIPTION", db.reviewEvents().forItem("TRANSCRIPTION", id).single().itemKind)
    service.finish(id)
    assertEquals(TranscriptionStatus.DONE, db.transcriptions().state(id)!!.status)
    service.clear(id)
    assertNull(db.transcriptions().state(id))
  }

  @Test
  fun anActiveSoloTakesItsShareBeforeTheTunes() = runTest {
    val id = bartley().id
    service.start(id)
    val solar = db.standards().bySlug("solar")!!.id
    val plan = PracticeService(db, random = Random(1)).plan(40, emptySet(), listOf(solar), Instrument.ALTO_SAX)
    val soloAt = plan.indexOfFirst { it is PlanBlock.Solo }
    assertEquals(soloMinutes(40), plan[soloAt].minutes)
    assertTrue(soloAt < plan.indexOfFirst { it is PlanBlock.Tune })
    assertEquals(40, plan.sumOf { it.minutes })
    assertTrue(PracticeService(db, random = Random(1)).plan(40, emptySet(), listOf(solar), Instrument.ALTO_SAX, null).none { it is PlanBlock.Solo })
    service.finish(id)
    assertTrue(PracticeService(db, random = Random(1)).plan(40, emptySet(), listOf(solar), Instrument.ALTO_SAX).none { it is PlanBlock.Solo })
    // A solo chosen at setup takes the place of the active one.
    val chosen = PracticeService(db, random = Random(1)).plan(40, emptySet(), listOf(solar), Instrument.ALTO_SAX, id).filterIsInstance<PlanBlock.Solo>()
    assertEquals(listOf(id), chosen.map { it.transcriptionId })
  }

  @Test
  fun yourOwnSolosLinkToTheTuneOfTheSameName() = runTest {
    val id = service.add(TranscriptionDraft(title = "solar", soloist = "Me", instrument = "Alto sax", url = "https://youtu.be/jNQXAC9IVRw"))
    assertEquals(db.standards().bySlug("solar")!!.id, db.transcriptions().byId(id)!!.standardId)
    service.edit(id, TranscriptionDraft(title = "Not A Real Tune", soloist = "Me", instrument = "Alto sax", url = "https://youtu.be/jNQXAC9IVRw"))
    assertNull(db.transcriptions().byId(id)!!.standardId)
  }

  @Test
  fun yourOwnSolosAndProgressAreBackedUp() = runTest {
    val mine = service.add(TranscriptionDraft(title = "Donna Lee", soloist = "Charlie Parker", instrument = "Alto sax", url = "https://youtu.be/jNQXAC9IVRw", start = "0:10", end = "1:00", recorded = "1947"))
    service.start(mine)
    service.setNotes(bartley().id, "First chorus done")
    val repository = WoodshedRepository(db, context.getSharedPreferences("solo-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }, context.assets)
    val backups = BackupService(db, repository)
    val text = backups.export()
    service.delete(mine)
    db.transcriptions().deleteAllStates()
    backups.restore(text)
    val restored = db.transcriptions().bySlug("user-donna-lee-charlie-parker")!!
    assertNull(restored.archivedAt)
    assertEquals(10, restored.startSeconds)
    assertEquals("1947", restored.recorded)
    assertEquals(TranscriptionStatus.ACTIVE, db.transcriptions().state(restored.id)!!.status)
    assertEquals("First chorus done", db.transcriptions().state(bartley().id)!!.notes)
  }

  @Test
  fun clockTimesReadBothWays() {
    assertEquals(120, parseClock("2:00"))
    assertEquals(329, parseClock("5:29"))
    assertEquals(3723, parseClock("1:02:03"))
    assertEquals(90, parseClock("90"))
    assertNull(parseClock("2:75"))
    assertNull(parseClock("soon"))
    assertEquals("5:29", clock(329))
    assertEquals("1:02:03", clock(3723))
  }

  @Test
  fun recordingDatesTakeADayAMonthOrAYear() {
    assertEquals("2022-08-13", parseRecorded(" 2022-08-13 "))
    assertEquals("2022-08", parseRecorded("2022-08"))
    assertEquals("1959", parseRecorded("1959"))
    assertNull(parseRecorded("2022-13-01"))
    assertNull(parseRecorded("Aug 2022"))
    assertEquals("Aug 13, 2022", recordedLabel("2022-08-13"))
    assertEquals("Aug 2022", recordedLabel("2022-08"))
    assertEquals("1959", recordedLabel("1959"))
  }

  @Test
  fun draftsExplainWhatIsWrong() {
    assertEquals(4, TranscriptionDraft().problems().size)
    assertEquals(
      listOf("The solo should end after it starts."),
      TranscriptionDraft(title = "X", soloist = "Y", instrument = "Z", url = "https://youtu.be/jNQXAC9IVRw", start = "3:00", end = "2:00").problems(),
    )
  }
}
