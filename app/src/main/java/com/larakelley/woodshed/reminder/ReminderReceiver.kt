package com.larakelley.woodshed.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.larakelley.woodshed.WoodshedApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The reminder's alarm and notification buttons, and the system events after which the alarm has to be set again. */
class ReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val reminders = (context.applicationContext as WoodshedApplication).reminders
    when (intent.action) {
      FIRE -> {
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
          try {
            reminders.fire()
          } finally {
            done.finish()
          }
        }
      }

      SNOOZE -> reminders.snooze()

      SKIP -> reminders.skipToday()

      // Alarms do not survive a restart or an update, and a new time zone moves the reminder's time.
      else -> reminders.reschedule()
    }
  }

  companion object {
    const val FIRE = "com.larakelley.woodshed.reminder.FIRE"
    const val SNOOZE = "com.larakelley.woodshed.reminder.SNOOZE"
    const val SKIP = "com.larakelley.woodshed.reminder.SKIP"
  }
}
