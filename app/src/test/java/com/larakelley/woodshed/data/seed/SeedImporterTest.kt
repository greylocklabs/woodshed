package com.larakelley.woodshed.data.seed

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.db.homeKey
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.Tonality
import com.larakelley.woodshed.domain.Family
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

/** Imports the actual bundled assets into a real in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SeedImporterTest {
  private lateinit var db: WoodshedDatabase
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val standardsJson = context.assets.open("standards.json").bufferedReader().use { it.readText() }
  private val exercisesJson = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
  private val seedRows = Json.parseToJsonElement(standardsJson).jsonArray

  @Before
  fun setUp() {
    db = Room.inMemoryDatabaseBuilder(context, WoodshedDatabase::class.java).allowMainThreadQueries().build()
  }

  @After
  fun tearDown() = db.close()

  @Test
  fun importsEveryRowFromTheAssets() = runTest {
    val result = SeedImporter(db).import(Seed.parse(standardsJson, exercisesJson))
    assertEquals(seedRows.size, db.standards().all().size)
    assertEquals(Json.parseToJsonElement(exercisesJson).jsonArray.size, db.exercises().all().size)
    assertEquals(seedRows.size + db.exercises().all().size, result.inserted)
  }

  @Test
  fun keepsUnknownDistinctFromEmpty() = runTest {
    SeedImporter(db).import(Seed.parse(standardsJson, exercisesJson))
    val nullFormsInSeed = seedRows.count { it.jsonObject["form"] is kotlinx.serialization.json.JsonNull }
    assertTrue(nullFormsInSeed > 0)
    assertEquals(nullFormsInSeed, db.standards().all().count { it.form == null })
    assertTrue(db.standards().all().none { it.form == "" || it.composer == "" || it.keyName == "" })
  }

  @Test
  fun storesKeyAndTonalitySeparately() = runTest {
    SeedImporter(db).import(Seed.parse(standardsJson, exercisesJson))
    val valentine = db.standards().bySlug("my-funny-valentine")!!
    assertEquals("C", valentine.keyName)
    assertEquals(0, valentine.keyPitchClass)
    assertEquals(Tonality.MINOR, valentine.tonality)
    assertEquals(Key(0, true), valentine.homeKey)
    val blues = db.standards().bySlug("blue-monk")!!
    assertEquals(Tonality.MAJOR, blues.tonality)
  }

  @Test
  fun familiesSurviveTheTypeConverter() = runTest {
    SeedImporter(db).import(Seed.parse(standardsJson, exercisesJson))
    assertEquals(emptyList<Family>(), db.exercises().byName("Long tones")!!.families)
    assertEquals(listOf(Family.SAXOPHONE), db.exercises().byName("Overtone series")!!.families)
    assertEquals(listOf(Family.SAXOPHONE, Family.CLARINET, Family.FLUTE), db.exercises().byName("Finger independence")!!.families)
  }

  @Test
  fun reseedKeepsIdsUpdatesFieldsAndArchivesDroppedRows() = runTest {
    val importer = SeedImporter(db, now = { 1_000L })
    importer.import(Seed.parse(standardsJson, exercisesJson))
    val idsBefore = db.standards().all().associate { it.slug to it.id }

    // Retitle one tune and drop another, as a later data file might.
    val rows = seedRows.toMutableList()
    val dropped = rows.removeAt(1).jsonObject["slug"]!!.jsonPrimitive.content
    rows[0] = JsonObject(rows[0].jsonObject + ("title" to JsonPrimitive("Retitled")))
    val result = importer.import(Seed.parse(JsonArray(rows).toString(), exercisesJson))

    assertEquals(0, result.inserted)
    assertEquals(1, result.archived)
    val after = db.standards().all().associateBy { it.slug }
    assertEquals(idsBefore, after.mapValues { it.value.id })
    assertEquals("Retitled", after.getValue(rows[0].jsonObject["slug"]!!.jsonPrimitive.content).title)
    assertEquals(1_000L, after.getValue(dropped).archivedAt)

    // Restoring the row brings back the same id, unarchived.
    importer.import(Seed.parse(standardsJson, exercisesJson))
    val restored = db.standards().bySlug(dropped)!!
    assertEquals(idsBefore[dropped], restored.id)
    assertNull(restored.archivedAt)
  }

  @Test
  fun rejectedSeedLeavesTheDatabaseAlone() = runTest {
    SeedImporter(db).import(Seed.parse(standardsJson, exercisesJson))
    val before = db.standards().all()
    val result = runCatching { SeedImporter(db).import(Seed.parse(standardsJson.dropLast(3), exercisesJson)) }
    assertNotNull(result.exceptionOrNull() as? SeedException)
    assertEquals(before, db.standards().all())
  }
}
