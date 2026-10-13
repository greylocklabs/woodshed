package com.larakelley.woodshed.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.seed.Seed
import com.larakelley.woodshed.data.seed.SeedImporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlaylistServiceTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private lateinit var db: WoodshedDatabase
  private lateinit var lists: PlaylistService
  private lateinit var repository: WoodshedRepository

  @Before
  fun setUp() = runTest {
    db = Room.inMemoryDatabaseBuilder(context, WoodshedDatabase::class.java).allowMainThreadQueries().build()
    val read = { name: String -> context.assets.open(name).bufferedReader().use { it.readText() } }
    SeedImporter(db).import(Seed.parse(read("standards.json"), read("exercises.json")))
    lists = PlaylistService(db, now = { 1L })
    repository = WoodshedRepository(db, context.getSharedPreferences("playlist-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }, context.assets)
  }

  @After
  fun tearDown() = db.close()

  private suspend fun id(slug: String) = db.standards().bySlug(slug)!!.id

  private suspend fun order(list: Long) = repository.playlists.first().single { it.id == list }.tuneIds

  @Test
  fun tunesKeepTheOrderTheyWereAddedInAndAreNotDuplicated() = runTest {
    val gig = lists.create("Friday gig")
    for (slug in listOf("solar", "autumn-leaves", "blue-bossa", "solar")) lists.add(gig, id(slug))
    assertEquals(listOf(id("solar"), id("autumn-leaves"), id("blue-bossa")), order(gig))
  }

  @Test
  fun movingAndRemovingRewriteTheOrder() = runTest {
    val gig = lists.create("Gig")
    for (slug in listOf("solar", "autumn-leaves", "blue-bossa")) lists.add(gig, id(slug))
    lists.move(gig, id("blue-bossa"), id("solar"))
    assertEquals(listOf(id("blue-bossa"), id("solar"), id("autumn-leaves")), order(gig))
    lists.remove(gig, id("solar"))
    assertEquals(listOf(id("blue-bossa"), id("autumn-leaves")), order(gig))
    assertEquals(listOf(0, 1), db.playlists().entries(gig).map { it.position })
    lists.move(gig, id("blue-bossa"), id("solar"))
    assertEquals(listOf(id("blue-bossa"), id("autumn-leaves")), order(gig))
    // A hidden tune between two shown ones stays put while they swap.
    lists.add(gig, id("solar"))
    lists.move(gig, id("solar"), id("blue-bossa"))
    assertEquals(listOf(id("solar"), id("blue-bossa"), id("autumn-leaves")), order(gig))
  }

  @Test
  fun renamingKeepsANameAndDeletingLeavesTheTunes() = runTest {
    val gig = lists.create("  Gig  ")
    lists.add(gig, id("solar"))
    lists.rename(gig, "")
    assertEquals("Gig", repository.playlists.first().single().name)
    lists.rename(gig, "Sunday jam")
    assertEquals("Sunday jam", repository.playlists.first().single().name)
    lists.delete(gig)
    assertTrue(repository.playlists.first().isEmpty())
    assertTrue(db.playlists().entries().isEmpty())
    assertEquals("Solar", db.standards().bySlug("solar")!!.title)
  }

  @Test
  fun rollsCanDrawFromAPlaylistOnly() = runTest {
    val gig = lists.create("Gig")
    lists.add(gig, id("solar"))
    lists.add(gig, id("nardis"))
    val practice = PracticeService(db, random = Random(3))
    val drawn = (1..30).mapNotNull { practice.roll(DiceConstraints(pool = TunePool.ANY, playlistId = gig))?.tune?.slug }.toSet()
    assertEquals(setOf("solar", "nardis"), drawn)
  }

  @Test
  fun backupsCarryPlaylistsInOrder() = runTest {
    val gig = lists.create("Gig")
    lists.add(gig, id("blue-bossa"))
    lists.add(gig, id("solar"))
    val backups = BackupService(db, repository)
    val text = backups.export()
    lists.delete(gig)
    assertEquals(1, backups.restore(text).playlists)
    val restored = repository.playlists.first().single()
    assertEquals("Gig", restored.name)
    assertEquals(listOf(id("blue-bossa"), id("solar")), restored.tuneIds)
  }
}
