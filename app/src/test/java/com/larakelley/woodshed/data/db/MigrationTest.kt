package com.larakelley.woodshed.data.db

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Installed copies already hold a version 1 database; upgrading must keep what is in it. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MigrationTest {
  @get:Rule
  val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WoodshedDatabase::class.java, listOf(WoodshedDatabase.DropBooks(), WoodshedDatabase.SplitKey()))

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

  @Test
  fun version3To4AddsBooksAndAllowsUnknownMeter() {
    helper.createDatabase("migration-test-3", 3).use { db ->
      db.execSQL(
        "INSERT INTO standards (id, slug, title, composer, year, bars, timeSignature, form, style, commonKeyRaw, commonKeyPitchClass, commonKeyMinor, archivedAt) " +
          "VALUES (7, 'solar', 'Solar', 'Miles Davis', 1954, 12, '4/4', NULL, 'Swing', 'C-', 0, 1, NULL)"
      )
      db.execSQL("INSERT INTO tune_state (standardId, lifecycle, keyPolicy, cyclePosition, reps, lapses, intervalDays, ease) VALUES (7, 'LEARNING', 'FIXED', 0, 2, 0, 3.0, 2.5)")
    }
    helper.runMigrationsAndValidate("migration-test-3", 4, true).use { db ->
      db.query("SELECT title, timeSignature, books, verified FROM standards WHERE id = 7").use {
        it.moveToFirst()
        assertEquals("Solar", it.getString(0))
        assertEquals("4/4", it.getString(1))
        assertEquals("", it.getString(2))
        assertEquals(1, it.getInt(3))
      }
      db.query("SELECT reps FROM tune_state WHERE standardId = 7").use { it.moveToFirst(); assertEquals(2, it.getInt(0)) }
      db.execSQL("INSERT INTO standards (slug, title, timeSignature, books, verified) VALUES ('x', 'X', NULL, 'Real Book 4', 0)")
    }
  }

  @Test
  fun version4To5ReplacesBooksWithCollection() {
    helper.createDatabase("migration-test-4", 4).use { db ->
      db.execSQL(
        "INSERT INTO standards (id, slug, title, composer, year, bars, timeSignature, form, style, commonKeyRaw, commonKeyPitchClass, commonKeyMinor, archivedAt, books, verified) " +
          "VALUES (7, 'solar', 'Solar', 'Miles Davis', 1954, 12, '4/4', NULL, 'Swing', 'C-', 0, 1, NULL, 'Real Book 1', 1)"
      )
      db.execSQL("INSERT INTO tune_state (standardId, lifecycle, keyPolicy, cyclePosition, reps, lapses, intervalDays, ease) VALUES (7, 'LEARNING', 'CYCLE', 3, 2, 0, 3.0, 2.5)")
      db.execSQL("INSERT INTO tune_knowledge (standardId, comfort, knownKeys, updatedAt) VALUES (7, 'SHAKY', 5, 0)")
    }
    helper.runMigrationsAndValidate("migration-test-4", 5, true).use { db ->
      db.query("SELECT title, collection, verified FROM standards WHERE id = 7").use {
        it.moveToFirst()
        assertEquals("Solar", it.getString(0))
        assertEquals("Standards", it.getString(1))
        assertEquals(1, it.getInt(2))
      }
      db.query("SELECT keyPolicy, cyclePosition FROM tune_state WHERE standardId = 7").use {
        it.moveToFirst()
        assertEquals("CYCLE", it.getString(0))
        assertEquals(3, it.getInt(1))
      }
      db.query("SELECT knownKeys FROM tune_knowledge WHERE standardId = 7").use { it.moveToFirst(); assertEquals(5, it.getInt(0)) }
    }
  }

  @Test
  fun version5To6SplitsKeyAndTonality() {
    helper.createDatabase("migration-test-5", 5).use { db ->
      db.execSQL(
        "INSERT INTO standards (id, slug, title, composer, year, bars, timeSignature, form, style, commonKeyRaw, commonKeyPitchClass, commonKeyMinor, archivedAt, collection, verified) " +
          "VALUES (7, 'solar', 'Solar', 'Miles Davis', 1954, 12, '4/4', NULL, 'Swing', 'C-', 0, 1, NULL, 'Standards', 1)"
      )
      db.execSQL("INSERT INTO tune_state (standardId, lifecycle, keyPolicy, cyclePosition, reps, lapses, intervalDays, ease) VALUES (7, 'LEARNING', 'CYCLE', 3, 2, 0, 3.0, 2.5)")
      db.execSQL("INSERT INTO tune_knowledge (standardId, comfort, knownKeys, updatedAt) VALUES (7, 'SHAKY', 5, 0)")
    }
    helper.runMigrationsAndValidate("migration-test-5", 6, true).use { db ->
      db.query("SELECT title, keyPitchClass, keyName, tonality FROM standards WHERE id = 7").use {
        it.moveToFirst()
        assertEquals("Solar", it.getString(0))
        assertEquals(0, it.getInt(1))
        assertTrue(it.isNull(2) && it.isNull(3))
      }
      db.query("SELECT keyPolicy, cyclePosition FROM tune_state WHERE standardId = 7").use {
        it.moveToFirst()
        assertEquals("CYCLE", it.getString(0))
        assertEquals(3, it.getInt(1))
      }
      db.query("SELECT knownKeys FROM tune_knowledge WHERE standardId = 7").use { it.moveToFirst(); assertEquals(5, it.getInt(0)) }
    }
  }

  @Test
  fun version6To7AddsYourTunesAndEditsWithoutTouchingPractice() {
    helper.createDatabase("migration-test-6", 6).use { db ->
      db.execSQL(
        "INSERT INTO standards (id, slug, title, composer, year, bars, timeSignature, form, style, keyName, keyPitchClass, tonality, archivedAt, collection, verified) " +
          "VALUES (7, 'solar', 'Solar', 'Miles Davis', 1954, 12, '4/4', NULL, 'Swing', 'C', 0, 'MINOR', NULL, 'Standards', 1)"
      )
      db.execSQL("INSERT INTO tune_state (standardId, lifecycle, keyPolicy, cyclePosition, reps, lapses, intervalDays, ease) VALUES (7, 'LEARNING', 'CYCLE', 3, 2, 0, 3.0, 2.5)")
    }
    helper.runMigrationsAndValidate("migration-test-6", 7, true).use { db ->
      db.query("SELECT userCreated FROM standards WHERE id = 7").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
      db.query("SELECT keyPolicy, cyclePosition FROM tune_state WHERE standardId = 7").use {
        it.moveToFirst()
        assertEquals("CYCLE", it.getString(0))
        assertEquals(3, it.getInt(1))
      }
      db.execSQL("INSERT INTO tune_overrides (standardId, keyName, keyPitchClass, updatedAt) VALUES (7, 'D', 2, 1)")
    }
  }

  @Test
  fun version7To8AddsPlaylists() {
    helper.createDatabase("migration-test-7", 7).use { db ->
      db.execSQL(
        "INSERT INTO standards (id, slug, title, composer, year, bars, timeSignature, form, style, keyName, keyPitchClass, tonality, archivedAt, collection, verified, userCreated) " +
          "VALUES (7, 'solar', 'Solar', 'Miles Davis', 1954, 12, '4/4', NULL, 'Swing', 'C', 0, 'MINOR', NULL, 'Standards', 1, 0)"
      )
    }
    helper.runMigrationsAndValidate("migration-test-7", 8, true).use { db ->
      db.execSQL("INSERT INTO playlists (id, name, createdAt) VALUES (1, 'Gig', 0)")
      db.execSQL("INSERT INTO playlist_tunes (playlistId, standardId, position) VALUES (1, 7, 0)")
      db.query("SELECT COUNT(*) FROM playlist_tunes").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }
  }

  @Test
  fun version8To9AddsYourExercisesAndEdits() {
    helper.createDatabase("migration-test-8", 8).use { db ->
      db.execSQL("INSERT INTO exercises (id, name, category, description, minutes, difficulty, tempoBpm, families, appliesToTune, archivedAt) VALUES (3, 'Long tones', 'Tone', 'Hold.', 10, 2, 60, '', 0, NULL)")
    }
    helper.runMigrationsAndValidate("migration-test-8", 9, true).use { db ->
      db.query("SELECT userCreated FROM exercises WHERE id = 3").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
      db.execSQL("INSERT INTO exercise_overrides (exerciseId, minutes, families, updatedAt) VALUES (3, 5, 'BRASS', 1)")
    }
  }

  @Test
  fun version9To10AddsTuneLinks() {
    helper.createDatabase("migration-test-9", 9).use { db ->
      db.execSQL(
        "INSERT INTO standards (id, slug, title, composer, year, bars, timeSignature, form, style, keyName, keyPitchClass, tonality, archivedAt, collection, verified, userCreated) " +
          "VALUES (7, 'solar', 'Solar', 'Miles Davis', 1954, 12, '4/4', NULL, 'Swing', 'C', 0, 'MINOR', NULL, 'Standards', 1, 0)"
      )
    }
    helper.runMigrationsAndValidate("migration-test-9", 10, true).use { db ->
      db.execSQL("INSERT INTO tune_media (standardId, url, title, position, createdAt) VALUES (7, 'https://youtu.be/jNQXAC9IVRw', NULL, 0, 0)")
      db.query("SELECT COUNT(*) FROM tune_media").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }
  }

  @Test
  fun version10To11AddsTranscriptions() {
    helper.createDatabase("migration-test-10", 10).use { db ->
      db.execSQL(
        "INSERT INTO standards (id, slug, title, composer, year, bars, timeSignature, form, style, keyName, keyPitchClass, tonality, archivedAt, collection, verified, userCreated) " +
          "VALUES (7, 'solar', 'Solar', 'Miles Davis', 1954, 12, '4/4', NULL, 'Swing', 'C', 0, 'MINOR', NULL, 'Standards', 1, 0)"
      )
    }
    helper.runMigrationsAndValidate("migration-test-10", 11, true).use { db ->
      db.execSQL("INSERT INTO transcriptions (id, slug, standardId, title, soloist, instrument, url, performers, userCreated) VALUES (1, 's', 7, 'Solar', 'Miles', 'Trumpet', 'https://youtu.be/x', '[]', 0)")
      db.execSQL("INSERT INTO transcription_state (transcriptionId, status, reps, lapses, intervalDays, ease) VALUES (1, 'ACTIVE', 0, 0, 0.0, 2.5)")
      db.query("SELECT COUNT(*) FROM transcription_state").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }
  }
}
