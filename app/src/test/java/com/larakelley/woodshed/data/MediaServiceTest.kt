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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MediaServiceTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private lateinit var db: WoodshedDatabase
  private lateinit var media: MediaService
  private lateinit var repository: WoodshedRepository

  @Before
  fun setUp() = runTest {
    db = Room.inMemoryDatabaseBuilder(context, WoodshedDatabase::class.java).allowMainThreadQueries().build()
    val read = { name: String -> context.assets.open(name).bufferedReader().use { it.readText() } }
    SeedImporter(db).import(Seed.parse(read("standards.json"), read("exercises.json")))
    media = MediaService(db, now = { 5L })
    repository = WoodshedRepository(db, context.getSharedPreferences("media-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }, context.assets)
  }

  @After
  fun tearDown() = db.close()

  @Test
  fun linksAttachInOrderAndNonLinksAreRefused() = runTest {
    val solar = db.standards().bySlug("solar")!!.id
    assertTrue(media.add(solar, " https://youtu.be/jNQXAC9IVRw ", "Miles"))
    assertTrue(media.add(solar, "https://open.spotify.com/track/abc", " "))
    assertFalse(media.add(solar, "not a link", null))
    val links = repository.media.first()
    assertEquals(listOf("https://youtu.be/jNQXAC9IVRw", "https://open.spotify.com/track/abc"), links.map { it.url })
    assertEquals(listOf("Miles", null), links.map { it.title })
    media.remove(links.first().id)
    assertEquals(1, repository.media.first().size)
  }

  @Test
  fun backupsCarryLinks() = runTest {
    val solar = db.standards().bySlug("solar")!!.id
    media.add(solar, "https://vimeo.com/76979871", "Clip")
    val backups = BackupService(db, repository)
    val text = backups.export()
    db.tuneMedia().deleteAll()
    backups.restore(text)
    val restored = repository.media.first().single()
    assertEquals(solar, restored.standardId)
    assertEquals("Clip", restored.title)
  }
}
