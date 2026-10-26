package com.larakelley.woodshed.data

import android.app.Application
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.domain.bars
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MyChangesTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private lateinit var db: WoodshedDatabase

  @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WoodshedDatabase::class.java)

  @Before
  fun setUp() {
    db = Room.inMemoryDatabaseBuilder(context, WoodshedDatabase::class.java).allowMainThreadQueries().build()
  }

  @After
  fun tearDown() = db.close()

  @Test
  fun changesAreSavedEditedAndDeleted() = runTest {
    val library = MyChangesLibrary(db) { 7L }
    val bridge = library.save(null, "  Stella bridge ", bars("IIm7b5 | V7b9 | Im7 | Im7"))
    library.save(null, "a minor vamp", bars("Im7 | IVm7"))
    assertEquals(listOf("a minor vamp", "Stella bridge"), library.all.first().map { it.name })
    library.save(bridge, "Stella bridge", bars("IIm7b5 V7b9 | Im7"))
    assertEquals(bars("IIm7b5 V7b9 | Im7"), library.all.first().single { it.id == bridge }.bars)
    library.delete(bridge)
    assertEquals(listOf("a minor vamp"), library.all.first().map { it.name })
  }

  @Test
  fun schemaFourteenGainsTheTableForThem() {
    migrations.createDatabase("migrate.db", 14).close()
    migrations.runMigrationsAndValidate("migrate.db", 15, true).close()
  }

  @Test
  fun schemaFifteenGainsNamesForChangesBlocks() {
    migrations.createDatabase("migrate.db", 15).close()
    migrations.runMigrationsAndValidate("migrate.db", 16, true).close()
  }
}
