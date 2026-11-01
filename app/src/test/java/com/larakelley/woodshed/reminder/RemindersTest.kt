package com.larakelley.woodshed.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.data.db.ReviewEventEntity
import com.larakelley.woodshed.data.db.SessionEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.ReminderSchedule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RemindersTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val zone = ZoneId.of("America/New_York")
  private lateinit var db: WoodshedDatabase
  private var schedule = ReminderSchedule(enabled = true, minuteOfDay = 18 * 60)

  // Sunday 2026-11-01, as the reminder goes off.
  private var now = ZonedDateTime.of(2026, 11, 1, 18, 0, 0, 0, zone).toInstant().toEpochMilli()
  private lateinit var reminders: Reminders

  private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
  private val alarm get() = shadowOf(context.getSystemService(AlarmManager::class.java)).peekNextScheduledAlarm()

  @Before
  fun setUp() {
    shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    db = Room.inMemoryDatabaseBuilder(context, WoodshedDatabase::class.java).allowMainThreadQueries().build()
    val prefs = context.getSharedPreferences("reminders-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
    reminders = Reminders(context, prefs, db, { schedule }, { zone }, { now })
    reminders.createChannel()
  }

  @After
  fun tearDown() = db.close()

  private fun hoursFromNow(hours: Int) = now + hours * 60 * 60 * 1000L

  @Test
  fun remindsThenSetsTheNextDay() = runTest {
    reminders.fire()
    assertEquals(1, notifications.size)
    assertEquals(hoursFromNow(24), alarm!!.triggerAtTime)
  }

  @Test
  fun quietOnADayAlreadyPractised() = runTest {
    db.sessions().insert(SessionEntity(startedAt = hoursFromNow(-1), plannedMinutes = 30, instrument = null))
    reminders.fire()
    assertEquals(0, notifications.size)
    assertEquals(hoursFromNow(24), alarm!!.triggerAtTime)
  }

  @Test
  fun aGradeTodayCountsAndPracticeYesterdayDoesNot() = runTest {
    db.sessions().insert(SessionEntity(startedAt = hoursFromNow(-20), plannedMinutes = 30, instrument = null))
    reminders.fire()
    assertEquals(1, notifications.size)

    context.getSystemService(NotificationManager::class.java).cancelAll()
    db.reviewEvents().insert(review(at = hoursFromNow(-1)))
    reminders.fire()
    assertEquals(0, notifications.size)
  }

  private fun review(at: Long) = ReviewEventEntity(
    itemKind = ReviewEventEntity.TUNE,
    itemId = 1,
    exerciseId = null,
    transposition = "C",
    keyWrittenPitchClass = null,
    keyWrittenMinor = null,
    grade = Grade.GOOD,
    reviewedAt = at,
    elapsedMs = 0,
    schedulerId = "test",
    schedulerVersion = 1,
    intervalBefore = 0.0,
    intervalAfter = 1.0,
    easeBefore = 2.5,
    easeAfter = 2.5,
  )

  @Test
  fun laterComesBackInAnHourAndSkipIsQuietUntilTomorrow() = runTest {
    reminders.fire()
    reminders.snooze()
    assertEquals(0, notifications.size)
    assertEquals(hoursFromNow(1), alarm!!.triggerAtTime)

    reminders.skipToday()
    assertEquals(hoursFromNow(24), alarm!!.triggerAtTime)
    now = hoursFromNow(1)
    reminders.fire()
    assertEquals(0, notifications.size)
  }

  @Test
  fun aSnoozeOutlastsTheAppStarting() = runTest {
    reminders.fire()
    reminders.snooze()
    // Opening the app sets the alarm again, which must not lose the snooze.
    now = hoursFromNow(0) + 20 * 60 * 1000L
    reminders.reschedule()
    assertEquals(hoursFromNow(1) - 20 * 60 * 1000L, alarm!!.triggerAtTime)
  }

  @Test
  fun aReminderDueButNotYetShownOutlastsTheAppStarting() {
    val sixPm = now
    now = hoursFromNow(-6)
    reminders.reschedule()
    assertEquals(sixPm, alarm!!.triggerAtTime)
    // The alarm is late, and the app opens at 18:03 before it goes off.
    now = sixPm + 3 * 60 * 1000L
    reminders.reschedule()
    assertEquals(sixPm, alarm!!.triggerAtTime)
  }

  @Test
  fun aNewTimeReplacesAnAlarmNotYetDue() {
    now = hoursFromNow(-6)
    reminders.reschedule()
    schedule = schedule.copy(minuteOfDay = 20 * 60)
    reminders.reschedule()
    assertEquals(hoursFromNow(8), alarm!!.triggerAtTime)
  }

  @Test
  fun turningItOffClearsTheAlarm() {
    reminders.reschedule()
    assertEquals(hoursFromNow(24), alarm!!.triggerAtTime)
    schedule = schedule.copy(enabled = false)
    reminders.reschedule()
    assertNull(alarm)
  }
}
