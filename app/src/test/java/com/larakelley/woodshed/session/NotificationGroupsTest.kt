package com.larakelley.woodshed.session

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.domain.ReminderSchedule
import com.larakelley.woodshed.reminder.Reminders
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NotificationGroupsTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()

  @Test
  fun everyChannelSitsUnderPractice() {
    val db = Room.inMemoryDatabaseBuilder(context, WoodshedDatabase::class.java).allowMainThreadQueries().build()
    SessionNotifications.createChannels(context)
    Reminders(context, context.getSharedPreferences("groups-test", Context.MODE_PRIVATE), db, { ReminderSchedule() }).createChannel()
    db.close()

    val manager = context.getSystemService(NotificationManager::class.java)
    assertEquals(listOf("Practice"), manager.notificationChannelGroups.map { it.name.toString() })
    assertEquals(setOf(NotificationGroups.PRACTICE), manager.notificationChannels.map { it.group }.toSet())
    assertEquals(3, manager.notificationChannels.size)
  }
}
