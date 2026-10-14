package com.larakelley.woodshed.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.data.db.WoodshedDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The seed import that runs at every launch: only when the bundled data changed, and never half-recorded. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SeedOnLaunchTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val bundled = { name: String -> context.assets.open(name).bufferedReader().use { it.readText() } }
  private lateinit var db: WoodshedDatabase
  private val prefs = context.getSharedPreferences("seed-launch-test", Context.MODE_PRIVATE)

  /** What the next launch reads, by file name; starts as the real bundled seed. */
  private val files = mutableMapOf<String, String>()

  @Before
  fun setUp() {
    prefs.edit().clear().commit()
    db = Room.inMemoryDatabaseBuilder(context, WoodshedDatabase::class.java).allowMainThreadQueries().build()
    for (name in listOf("standards.json", "exercises.json", "transcriptions.json")) files[name] = bundled(name)
  }

  @After
  fun tearDown() = db.close()

  private fun repository() = WoodshedRepository(db, prefs, context.assets, readSeed = { files.getValue(it) })

  private suspend fun title(slug: String) = db.standards().bySlug(slug)!!.title

  @Test
  fun anUnchangedSeedIsNotImportedAgain() = runTest {
    repository().seedIfChanged()
    // A change only an import would undo stays put when the bundled seed has not changed.
    val solar = db.standards().bySlug("solar")!!
    db.standards().update(listOf(solar.copy(title = "Changed here")))
    repository().seedIfChanged()
    assertEquals("Changed here", title("solar"))
  }

  @Test
  fun aChangedSeedIsImported() = runTest {
    repository().seedIfChanged()
    files["standards.json"] = files.getValue("standards.json").replace("\"title\": \"Solar\"", "\"title\": \"Solar (corrected)\"")
    repository().seedIfChanged()
    assertEquals("Solar (corrected)", title("solar"))
  }

  @Test
  fun aBrokenSeedReportsItselfAndIsTriedAgainOnceFixed() = runTest {
    val good = files.getValue("standards.json")
    files["standards.json"] = good.dropLast(3)
    val broken = repository()
    broken.seedIfChanged()
    assertNotNull(broken.seedError.value)
    assertNull(db.standards().bySlug("solar"))

    // Its hash was not recorded, so the next launch with good data imports instead of skipping.
    files["standards.json"] = good
    val fixed = repository()
    fixed.seedIfChanged()
    assertNull(fixed.seedError.value)
    assertEquals("Solar", title("solar"))
  }
}
