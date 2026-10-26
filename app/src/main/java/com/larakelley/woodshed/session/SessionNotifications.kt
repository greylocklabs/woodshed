package com.larakelley.woodshed.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.larakelley.woodshed.R
import com.larakelley.woodshed.ui.practice.StepView

/** The session's ongoing notification and its time's-up alert. */
object SessionNotifications {
  const val ONGOING_ID = 1
  const val ALERT_ID = 2
  private const val ONGOING_CHANNEL = "session"
  private const val ALERT_CHANNEL = "session_alert"

  fun createChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
      NotificationChannel(ONGOING_CHANNEL, "Practice session", NotificationManager.IMPORTANCE_LOW).apply {
        description = "The block you're on and its time left"
      },
    )
    manager.createNotificationChannel(
      NotificationChannel(ALERT_CHANNEL, "Block time's up", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "When a block's planned time runs out"
      },
    )
  }

  fun ongoing(context: Context, s: SessionState): Notification {
    val step = s.step
    val builder = NotificationCompat.Builder(context, ONGOING_CHANNEL)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(step?.let(::title) ?: "Practice session")
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
      .setContentIntent(openApp(context))
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    if (step == null) return builder.build()

    val ends = s.stepEndsAt ?: 0L
    val paused = s.pausedAt != null
    val over = !paused && System.currentTimeMillis() >= ends
    builder.setContentText(
      listOfNotNull("Block ${s.stepIndex + 1} of ${s.stepCount}", "paused".takeIf { paused }, "time's up".takeIf { over }).joinToString(" · "),
    )
    // A countdown to the block's end that the system keeps ticking; paused, it shows the time left frozen instead.
    if (paused) {
      builder.setShowWhen(false).setSubText(remaining(ends - s.pausedAt))
    } else {
      builder.setWhen(ends).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
    }
    builder.addAction(0, if (step is StepView.Exercise) "Done" else "Skip", action(context, SessionActionReceiver.NEXT))
    builder.addAction(0, if (paused) "Resume" else "Pause", action(context, if (paused) SessionActionReceiver.RESUME else SessionActionReceiver.PAUSE))
    builder.addAction(0, "End", action(context, SessionActionReceiver.STOP))
    return builder.build()
  }

  fun timesUp(context: Context, s: SessionState): Notification = NotificationCompat.Builder(context, ALERT_CHANNEL)
    .setSmallIcon(R.drawable.ic_notification)
    .setContentTitle("Time's up")
    .setContentText(s.step?.let { "${title(it)}: move on when you reach a stopping point" } ?: "")
    .setCategory(NotificationCompat.CATEGORY_ALARM)
    .setPriority(NotificationCompat.PRIORITY_HIGH)
    .setAutoCancel(true)
    .setContentIntent(openApp(context))
    .build()

  /** The block as one line, such as "Long tones" or "Solar: Melody from memory". */
  fun title(step: StepView): String = when (step) {
    is StepView.Exercise -> step.name
    is StepView.Tune -> "${step.card.title}: ${step.card.exercise}"
    is StepView.Solo -> "Transcribe ${step.title}"
    is StepView.Changes -> "${step.title} in ${step.key}"
  }

  private fun remaining(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d left".format(seconds / 60, seconds % 60)
  }

  private fun openApp(context: Context): PendingIntent {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
      .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  }

  private fun action(context: Context, what: String): PendingIntent {
    val intent = Intent(context, SessionActionReceiver::class.java).setAction(what)
    return PendingIntent.getBroadcast(context, what.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  }
}
