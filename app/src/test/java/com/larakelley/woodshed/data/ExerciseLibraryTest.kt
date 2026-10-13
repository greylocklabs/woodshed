package com.larakelley.woodshed.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.seed.Seed
import com.larakelley.woodshed.data.seed.SeedImporter
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Instrument
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExerciseLibraryTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val read = { name: String -> context.assets.open(name).bufferedReader().use { it.readText() } }
  private lateinit var db: WoodshedDatabase
  private lateinit var library: ExerciseLibrary
  private lateinit var repository: WoodshedRepository

  @Before
  fun setUp() = runTest {
    db = Room.inMemoryDatabaseBuilder(context, WoodshedDatabase::class.java).allowMainThreadQueries().build()
    SeedImporter(db).import(Seed.parse(read("standards.json"), read("exercises.json")))
    library = ExerciseLibrary(db, now = { 9L })
    repository = WoodshedRepository(db, context.getSharedPreferences("exercise-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }, context.assets)
  }

  @After
  fun tearDown() = db.close()

  private val mine = ExerciseDraft(name = "Tongue twisters", category = "Articulation", description = "Ta-ka-ta-ka on one note.", minutes = "6", families = setOf(Family.BRASS))

  @Test
  fun addedExercisesShowUpAndSurviveReseeding() = runTest {
    val id = library.add(mine)
    SeedImporter(db).import(Seed.parse(read("standards.json"), read("exercises.json")))
    val shown = repository.exercises.first().single { it.id == id }
    assertTrue(shown.userCreated)
    assertEquals(listOf(Family.BRASS), shown.families)
    assertNull(shown.archivedAt)
  }

  @Test
  fun aNewSeedExerciseWithYourNameTakesItAndYoursIsRenamed() = runTest {
    val id = library.add(mine.copy(name = "Brand new seed drill"))
    val exercises = Json.parseToJsonElement(read("exercises.json")).jsonArray.toMutableList()
    exercises += JsonObject(
      exercises.first().jsonObject.toMutableMap().apply {
        put("name", JsonPrimitive("Brand new seed drill"))
      }
    )
    SeedImporter(db).import(Seed.parse(read("standards.json"), JsonArray(exercises).toString()))
    assertEquals("Brand new seed drill (yours)", db.exercises().byId(id)!!.name)
    assertFalse(db.exercises().byName("Brand new seed drill")!!.userCreated)
  }

  @Test
  fun editingASeededExerciseStoresOnlyChangesAndResetDropsThem() = runTest {
    val longTones = db.exercises().byName("Long tones")!!
    library.edit(longTones.id, ExerciseDraft.of(longTones).copy(minutes = "5"))
    val o = db.exerciseOverrides().get(longTones.id)!!
    assertEquals(5, o.minutes)
    assertNull(o.name)
    assertNull(o.families)
    assertEquals(5, repository.exercises.first().single { it.id == longTones.id }.minutes)
    library.resetToOriginal(longTones.id)
    assertNull(db.exerciseOverrides().get(longTones.id))
  }

  @Test
  fun sessionsUseYourExercisesAndEdits() = runTest {
    val id = library.add(mine.copy(category = "Technique"))
    val plan = PracticeService(db, random = Random(1)).plan(240, setOf(com.larakelley.woodshed.domain.Focus.SPEED), emptyList(), Instrument.TRUMPET)
    assertTrue(plan.any { (it as? com.larakelley.woodshed.domain.PlanBlock.Exercise)?.exerciseId == id })
    val flute = PracticeService(db, random = Random(1)).plan(240, setOf(com.larakelley.woodshed.domain.Focus.SPEED), emptyList(), Instrument.FLUTE)
    assertTrue(flute.none { (it as? com.larakelley.woodshed.domain.PlanBlock.Exercise)?.exerciseId == id })
  }

  @Test
  fun namesMustBeUniqueAndDeletingArchivesYourOwnOnly() = runTest {
    assertTrue(library.nameTaken("long TONES"))
    val id = library.add(mine)
    library.delete(id)
    assertTrue(repository.exercises.first().none { it.id == id })
    val longTones = db.exercises().byName("Long tones")!!
    library.delete(longTones.id)
    assertNull(db.exercises().byId(longTones.id)!!.archivedAt)
  }

  @Test
  fun draftsExplainWhatIsWrong() {
    assertEquals(3, ExerciseDraft(minutes = "0").problems().size)
    assertEquals(listOf("Another exercise already has that name."), mine.problems { true })
    assertTrue(mine.problems().isEmpty())
  }

  @Test
  fun backupsCarryYourExercisesAndEdits() = runTest {
    val id = library.add(mine)
    val longTones = db.exercises().byName("Long tones")!!
    library.edit(longTones.id, ExerciseDraft.of(longTones).copy(minutes = "5"))
    val backups = BackupService(db, repository)
    val text = backups.export()
    library.delete(id)
    library.resetToOriginal(longTones.id)
    backups.restore(text)
    assertNull(db.exercises().byName("Tongue twisters")!!.archivedAt)
    assertEquals(5, db.exerciseOverrides().get(longTones.id)!!.minutes)
  }

  @Test
  fun clearingASeededTempoStaysCleared() = runTest {
    val seeded = repository.exercises.first().first { !it.userCreated && it.tempoBpm != null }
    library.edit(seeded.id, ExerciseDraft.of(seeded).copy(tempoBpm = ""))
    assertNull(repository.exercises.first().single { it.id == seeded.id }.tempoBpm)
  }

  @Test
  fun aNameARenamedSeedExerciseGaveUpCanBeUsedAgain() = runTest {
    val seeded = repository.exercises.first().first { !it.userCreated }
    library.edit(seeded.id, ExerciseDraft.of(seeded).copy(name = "${seeded.name} 2"))
    // The seed row still holds the name underneath, so the user's exercise steps aside rather than crashing.
    val id = library.add(mine.copy(name = seeded.name))
    assertEquals("${seeded.name} (yours)", db.exercises().byId(id)!!.name)
  }
}
