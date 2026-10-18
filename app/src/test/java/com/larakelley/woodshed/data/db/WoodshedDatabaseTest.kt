package com.larakelley.woodshed.data.db

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WoodshedDatabaseTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()

  @Test
  fun aDatabaseFromAnEarlyReleaseStartsOver() = runTest {
    val file = context.getDatabasePath("woodshed.db")
    file.parentFile?.mkdirs()
    SQLiteDatabase.openOrCreateDatabase(file, null).use {
      it.execSQL("CREATE TABLE standards (id INTEGER PRIMARY KEY)")
      it.execSQL("INSERT INTO standards VALUES (1)")
      it.version = 13
    }

    val db = WoodshedDatabase.create(context)
    try {
      assertTrue(db.standards().all().isEmpty())
      assertEquals(14, db.openHelper.readableDatabase.version)
    } finally {
      db.close()
    }
  }
}
