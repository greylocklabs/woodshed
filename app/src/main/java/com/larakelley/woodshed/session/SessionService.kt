package com.larakelley.woodshed.session

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.larakelley.woodshed.WoodshedApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps a practice session going with the screen off or another app in front: it holds the ongoing notification,
 * keeps the CPU awake so the time's-up alert lands on time, and keeps the process alive for the metronome.
 * It stops itself when the session ends.
 */
class SessionService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var wakeLock: PowerManager.WakeLock? = null
  private var alert: Job? = null
  private var alertedBlock: Int? = null

  override fun onBind(intent: Intent?) = null

  override fun onCreate() {
    super.onCreate()
    val sessions = (application as WoodshedApplication).sessions
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
    ServiceCompat.startForeground(this, SessionNotifications.ONGOING_ID, SessionNotifications.ongoing(this, sessions.state.value), type)
    wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "woodshed:session").apply {
      setReferenceCounted(false)
      acquire(MAX_HOLD_MS)
    }
    scope.launch {
      // After a restart the saved session may still be loading; deciding before then would end it.
      sessions.ready.await()
      sessions.state.collect { s ->
        if (!s.running || s.step == null) {
          stopSelf()
          return@collect
        }
        notifications().notify(SessionNotifications.ONGOING_ID, SessionNotifications.ongoing(this@SessionService, s))
        scheduleAlert(s)
      }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

  override fun onDestroy() {
    scope.cancel()
    notifications().cancel(SessionNotifications.ALERT_ID)
    wakeLock?.takeIf { it.isHeld }?.release()
    super.onDestroy()
  }

  /** Alerts once when the current block's time runs out, unless the session is paused or moves on first. */
  private fun scheduleAlert(s: SessionState) {
    alert?.cancel()
    // A time's-up alert belongs to its block; it goes once the session moves on.
    if (alertedBlock != null && alertedBlock != s.stepIndex) {
      notifications().cancel(SessionNotifications.ALERT_ID)
      alertedBlock = null
    }
    val ends = s.stepEndsAt ?: return
    if (s.pausedAt != null) return
    val wait = ends - System.currentTimeMillis()
    if (wait <= 0) return
    alert = scope.launch {
      delay(wait)
      alertedBlock = s.stepIndex
      notifications().notify(SessionNotifications.ALERT_ID, SessionNotifications.timesUp(this@SessionService, s))
      notifications().notify(SessionNotifications.ONGOING_ID, SessionNotifications.ongoing(this@SessionService, s))
    }
  }

  private fun notifications() = getSystemService(NotificationManager::class.java)

  private companion object {
    /** Longer than any session the planner makes, so a forgotten one cannot hold the CPU forever. */
    const val MAX_HOLD_MS = 4 * 60 * 60 * 1000L
  }
}

/** The notification's buttons. */
class SessionActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val sessions = (context.applicationContext as WoodshedApplication).sessions
    when (intent.action) {
      NEXT -> sessions.next()
      PAUSE -> sessions.pause()
      RESUME -> sessions.resume()
      STOP -> sessions.stop()
    }
  }

  companion object {
    const val NEXT = "com.larakelley.woodshed.session.NEXT"
    const val PAUSE = "com.larakelley.woodshed.session.PAUSE"
    const val RESUME = "com.larakelley.woodshed.session.RESUME"
    const val STOP = "com.larakelley.woodshed.session.STOP"
  }
}
