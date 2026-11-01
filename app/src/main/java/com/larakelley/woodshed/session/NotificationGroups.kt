package com.larakelley.woodshed.session

import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context

/** The heading Android's notification settings show Woodshed's channels under, in place of "Other". */
object NotificationGroups {
  const val PRACTICE = "practice"

  fun create(context: Context) {
    context.getSystemService(NotificationManager::class.java).createNotificationChannelGroup(NotificationChannelGroup(PRACTICE, "Practice"))
  }
}
