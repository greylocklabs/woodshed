package com.larakelley.woodshed.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.seed.Seed
import com.larakelley.woodshed.data.seed.SeedImporter
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.PlanBlock
import com.larakelley.woodshed.domain.Tonality
import com.larakelley.woodshed.domain.Transposition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TuneLibraryTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val read = { name: String -> context.assets.open(name).bufferedReader().use { it.readText() } }
  private lateinit var db: WoodshedDatabase
  private lateinit var library: TuneLibrary
  private lateinit var repository: WoodshedRepository

  @Before
  fun setUp() = runTest {
    db = Room.inMemoryDatabaseBuilder(context, WoodshedDatabase::class.java).allowMainThreadQueries().build()
    SeedImporter(db).import(Seed.parse(read("standards.json"), read("exercises.json")))
    library = TuneLibrary(db, now = { 7L })
    repository = WoodshedRepository(db, context.getSharedPreferences("library-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }, context.assets)
  }

  @After
  fun tearDown() = db.close()

  private val mine = TuneDraft(title = "Blues for Woody", composer = "Me", year = "2026", bars = "12", form = "Blues", keyName = "F", tonality = Tonality.MAJOR)

  @Test
  fun addedTunesGetTheirOwnSlugsAndSurviveReseeding() = runTest {
    val first = library.add(mine)
    val second = library.add(mine)
    assertEquals("user-blues-for-woody", first)
    assertEquals("user-blues-for-woody-2", second)

    SeedImporter(db).import(Seed.parse(read("standards.json"), read("exercises.json")))
    val row = db.standards().bySlug(first)!!
    assertTrue(row.userCreated)
    assertNull(row.archivedAt)
    assertEquals(5, row.keyPitchClass)
  }

  @Test
  fun editingASeededTuneStoresOnlyTheChangesAndSurvivesReseeding() = runTest {
    val solar = db.standards().bySlug("solar")!!
    library.edit(solar.id, TuneDraft.of(solar).copy(keyName = "D", composer = solar.composer.orEmpty()))
    val override = db.tuneOverrides().get(solar.id)!!
    assertEquals("D", override.keyName)
    assertEquals(2, override.keyPitchClass)
    assertNull("unchanged fields are not stored", override.title)
    assertNull(override.composer)

    SeedImporter(db).import(Seed.parse(read("standards.json"), read("exercises.json")))
    val shown = repository.standards.first().single { it.slug == "solar" }
    assertEquals("D", shown.keyName)
    assertEquals(solar.title, shown.title)
    assertEquals("C", db.standards().bySlug("solar")!!.keyName)
  }

  @Test
  fun anEditThatMatchesTheOriginalLeavesNothingBehindAndResetUndoesEdits() = runTest {
    val solar = db.standards().bySlug("solar")!!
    library.edit(solar.id, TuneDraft.of(solar))
    assertNull(db.tuneOverrides().get(solar.id))
    library.edit(solar.id, TuneDraft.of(solar).copy(title = "Solar (Miles)"))
    assertNotNull(db.tuneOverrides().get(solar.id))
    library.resetToOriginal(solar.id)
    assertNull(db.tuneOverrides().get(solar.id))
  }

  @Test
  fun deletingYourTuneTakesItOutOfListsAndPracticeButSeededTunesCannotBeDeleted() = runTest {
    val slug = library.add(mine)
    val id = db.standards().bySlug(slug)!!.id
    val practice = PracticeService(db, random = Random(1))
    practice.startLearning(id)
    library.delete(id)
    assertNull(db.tuneStates().get(id))
    assertTrue(repository.standards.first().none { it.slug == slug })

    val solar = db.standards().bySlug("solar")!!
    library.delete(solar.id)
    assertNull(db.standards().bySlug("solar")!!.archivedAt)
  }

  @Test
  fun cardsUseTheEditedKey() = runTest {
    val solar = db.standards().bySlug("solar")!!
    library.edit(solar.id, TuneDraft.of(solar).copy(keyName = "D"))
    val practice = PracticeService(db, random = Random(1))
    practice.startLearning(solar.id)
    val tuneExercise = db.exercises().all().first { it.appliesToTune }.id
    val card = practice.cardForBlock(PlanBlock.Tune(solar.id, tuneExercise, 10), Transposition.C)!!
    assertEquals("D minor", card.concertKey!!.displayName)
  }

  @Test
  fun draftsExplainWhatIsWrong() {
    assertEquals(listOf("A title is needed."), TuneDraft().problems())
    assertEquals(2, TuneDraft(title = "X", year = "nineteen", bars = "-4").problems().size)
    assertTrue(mine.problems().isEmpty())
  }

  @Test
  fun backupsCarryYourTunesAndEdits() = runTest {
    val slug = library.add(mine)
    val solar = db.standards().bySlug("solar")!!
    library.edit(solar.id, TuneDraft.of(solar).copy(keyName = "D"))
    PracticeService(db, random = Random(1)).addToWishlist(db.standards().bySlug(slug)!!.id)
    val backups = BackupService(db, repository)
    val text = backups.export()

    library.delete(db.standards().bySlug(slug)!!.id)
    library.resetToOriginal(solar.id)
    val result = backups.restore(text)

    assertEquals(1, result.myTunes)
    assertEquals(1, result.edits)
    val restored = db.standards().bySlug(slug)!!
    assertNull(restored.archivedAt)
    assertEquals(Lifecycle.WISHLIST, db.tuneStates().get(restored.id)!!.lifecycle)
    assertEquals("D", db.tuneOverrides().get(solar.id)!!.keyName)
  }

  @Test
  fun slugsAreAsciiWords() {
    assertEquals("cest-si-bon", TuneLibrary.slugify("C'est Si Bon"))
    assertEquals("salt-and-pepper", TuneLibrary.slugify("Salt & Pepper"))
    assertEquals("dexters-tune", TuneLibrary.slugify("  Dexter's Tune!  "))
  }

  @Test
  fun blankingASeededFieldStaysBlankThroughABackup() = runTest {
    val id = db.standards().bySlug("solar")!!.id
    val seen = repository.standards.first().single { it.id == id }
    assertTrue(seen.composer != null)
    library.edit(id, TuneDraft.of(seen).copy(composer = ""))
    assertNull(repository.standards.first().single { it.id == id }.composer)

    val backups = BackupService(db, repository)
    val text = backups.export()
    db.tuneOverrides().deleteAll()
    backups.restore(text)
    assertNull(repository.standards.first().single { it.id == id }.composer)
  }
}
