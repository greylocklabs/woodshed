package com.larakelley.woodshed.data.db

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Installed copies already hold a version 1 database; upgrading must keep what is in it. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MigrationTest {
  @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WoodshedDatabase::class.java)

  @Test
  fun version1To2KeepsSeededRowsAndAddsPracticeTables() {
    helper.createDatabase("migration-test", 1).use { db ->
      db.execSQL(
        "INSERT INTO standards (id, slug, title, composer, year, bars, timeSignature, form, style, commonKeyRaw, commonKeyPitchClass, commonKeyMinor, archivedAt) " +
          "VALUES (7, 'solar', 'Solar', 'Miles Davis', 1954, 12, '4/4', NULL, 'Swing', 'C-', 0, 1, NULL)"
      )
    }
    helper.runMigrationsAndValidate("migration-test", 2, true).use { db ->
      db.query("SELECT id, title FROM standards").use {
        it.moveToFirst()
        assertEquals(7L, it.getLong(0))
        assertEquals("Solar", it.getString(1))
      }
      db.execSQL("INSERT INTO tune_state (standardId, lifecycle, keyPolicy, cyclePosition, reps, lapses, intervalDays, ease) VALUES (7, 'LEARNING', 'FIXED', 0, 0, 0, 0.0, 2.5)")
      db.query("SELECT COUNT(*) FROM review_events").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }
  }

  @Test
  fun version2To3KeepsPracticeStateAndAddsKnowledge() {
    helper.createDatabase("migration-test-2", 2).use { db ->
      db.execSQL(
        "INSERT INTO standards (id, slug, title, composer, year, bars, timeSignature, form, style, commonKeyRaw, commonKeyPitchClass, commonKeyMinor, archivedAt) " +
          "VALUES (7, 'solar', 'Solar', 'Miles Davis', 1954, 12, '4/4', NULL, 'Swing', 'C-', 0, 1, NULL)"
      )
      db.execSQL("INSERT INTO tune_state (standardId, lifecycle, keyPolicy, cyclePosition, reps, lapses, intervalDays, ease, dueAt) VALUES (7, 'LEARNING', 'FIXED', 0, 2, 0, 3.0, 2.5, 123)")
    }
    helper.runMigrationsAndValidate("migration-test-2", 3, true).use { db ->
      db.query("SELECT reps, intervalDays, dueAt FROM tune_state WHERE standardId = 7").use {
        it.moveToFirst()
        assertEquals(2, it.getInt(0))
        assertEquals(3.0, it.getDouble(1), 0.0)
        assertEquals(123L, it.getLong(2))
      }
      db.execSQL("INSERT INTO tune_knowledge (standardId, comfort, knownKeys, updatedAt) VALUES (7, 'SHAKY', 1, 0)")
    }
  }
}
