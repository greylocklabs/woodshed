package com.larakelley.woodshed.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.larakelley.woodshed.R
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.domain.ReminderSchedule
import com.larakelley.woodshed.domain.nextReminder
import com.larakelley.woodshed.session.NotificationGroups
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The daily practice reminder: one alarm at a time, for the next chosen day, which posts a notification unless the
 * user has practiced today or skipped it. The alarm is inexact, so it needs no exact-alarm permission and may come a
 * few minutes late.
 */
class Reminders(
  private val context: Context,
  private val prefs: SharedPreferences,
  private val db: WoodshedDatabase,
  private val schedule: () -> ReminderSchedule,
  private val zone: () -> ZoneId = ZoneId::systemDefault,
  private val now: () -> Long = System::currentTimeMillis,
) {
  private val alarms get() = context.getSystemService(AlarmManager::class.java)

  fun createChannel() {
    NotificationGroups.create(context)
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
      NotificationChannel(CHANNEL, "Practice reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "A nudge to practice, on the days and at the time you choose"
        group = NotificationGroups.PRACTICE
      },
    )
  }

  /**
   * Sets the alarm for the next reminder, or clears it when the reminder is off. An alarm that has not gone off yet
   * stays when it is sooner: a snooze, or a reminder already due, as the alarm can come late.
   */
  fun reschedule() {
    val next = nextReminder(schedule(), ZonedDateTime.ofInstant(Instant.ofEpochMilli(now()), zone()))?.toInstant()?.toEpochMilli()
    if (next == null) {
      alarms.cancel(alarm())
      prefs.edit {
        remove(DUE_AT)
        remove(SNOOZED)
      }
      NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
      return
    }
    val due = prefs.getLong(DUE_AT, 0L)
    val snoozed = prefs.getBoolean(SNOOZED, false)
    val pending = due > 0 && due < next && (snoozed || due in now() - LATE_MS..now())
    if (pending) at(due, snoozed) else at(next, snoozed = false)
  }

  /** The alarm went off: remind, unless the reminder was turned off or today needs none, then set the next one. */
  suspend fun fire() {
    prefs.edit {
      remove(DUE_AT)
      remove(SNOOZED)
    }
    if (schedule().enabled && !skippedToday() && !practisedToday()) post()
    reschedule()
  }

  /** Brings the reminder back in an hour, once. The day's next alarm is replaced, as the reminder is today's. */
  fun snooze() {
    NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    at(now() + SNOOZE_MS, snoozed = true)
  }

  /** No reminder again today, even from a snooze. */
  fun skipToday() {
    NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    prefs.edit {
      putString(SKIPPED, today().toString())
      remove(DUE_AT)
      remove(SNOOZED)
    }
    reschedule()
  }

  /** A session started, or anything graded, since midnight. */
  private suspend fun practisedToday(): Boolean {
    val midnight = today().atStartOfDay(zone()).toInstant().toEpochMilli()
    return db.sessions().countSince(midnight) > 0 || db.reviewEvents().countSince(midnight) > 0
  }

  private fun skippedToday() = prefs.getString(SKIPPED, null) == today().toString()

  private fun today() = Instant.ofEpochMilli(now()).atZone(zone()).toLocalDate()

  /** Sets the one alarm, and remembers it, so an app start or a time change can tell it has not gone off yet. */
  private fun at(millis: Long, snoozed: Boolean) {
    alarms.setWindow(AlarmManager.RTC_WAKEUP, millis, WINDOW_MS, alarm())
    prefs.edit {
      putLong(DUE_AT, millis)
      putBoolean(SNOOZED, snoozed)
    }
  }

  private fun post() {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
    val notification = NotificationCompat.Builder(context, CHANNEL)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("Time to practice")
      .setContentText("Ready to plan today's session?")
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .setAutoCancel(true)
      .setContentIntent(startSession())
      .addAction(0, "Start a session", startSession())
      .addAction(0, "Later", action(ReminderReceiver.SNOOZE))
      .addAction(0, "Skip today", action(ReminderReceiver.SKIP))
      .build()
    try {
      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    } catch (_: SecurityException) {
      // The notification permission was taken away after the check above.
    }
  }

  private fun alarm() = action(ReminderReceiver.FIRE)

  private fun action(what: String): PendingIntent {
    val intent = Intent(context, ReminderReceiver::class.java).setAction(what)
    return PendingIntent.getBroadcast(context, what.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  }

  /** Opens the app on the Practice tab, set up with the user's usual session. */
  private fun startSession(): PendingIntent {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
      .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      .putExtra(EXTRA_OPEN, OPEN_PRACTICE)
    return PendingIntent.getActivity(context, NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  }

  companion object {
    const val EXTRA_OPEN = "open"
    const val OPEN_PRACTICE = "practice"
    const val NOTIFICATION_ID = 10
    private const val CHANNEL = "practice_reminder"
    private const val SKIPPED = "reminder_skipped_on"
    private const val DUE_AT = "reminder_due_at"
    private const val SNOOZED = "reminder_snoozed"
    private const val SNOOZE_MS = 60 * 60 * 1000L
    private const val WINDOW_MS = 10 * 60 * 1000L

    /** How late a due reminder can still come; past this, as after a force stop, it is let go. */
    private const val LATE_MS = 30 * 60 * 1000L
  }
}
