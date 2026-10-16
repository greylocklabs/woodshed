package com.larakelley.woodshed

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.larakelley.woodshed.audio.Drone
import com.larakelley.woodshed.audio.Metronome
import com.larakelley.woodshed.audio.Tuner
import com.larakelley.woodshed.data.BackupService
import com.larakelley.woodshed.data.ExerciseLibrary
import com.larakelley.woodshed.data.MediaService
import com.larakelley.woodshed.data.PlaylistService
import com.larakelley.woodshed.data.PracticeService
import com.larakelley.woodshed.data.ReviewPrompt
import com.larakelley.woodshed.data.TranscriptionService
import com.larakelley.woodshed.data.TuneLibrary
import com.larakelley.woodshed.data.WhatsNew
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.session.SessionNotifications
import com.larakelley.woodshed.session.SessionRunner
import com.larakelley.woodshed.session.SessionService
import com.larakelley.woodshed.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class WoodshedApplication : Application() {
  private val database by lazy { WoodshedDatabase.create(this) }
  private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }

  val repository: WoodshedRepository by lazy { WoodshedRepository(database, prefs, assets) }

  val metronome: Metronome by lazy { Metronome(prefs) }

  val tuner: Tuner by lazy { Tuner(prefs) }

  /** Shares the tuner's reference pitch, so the two always agree. */
  val drone: Drone by lazy { Drone(prefs) { tuner.state.value.a4 } }

  val practice: PracticeService by lazy { PracticeService(database) }

  val backups: BackupService by lazy { BackupService(database, repository) }

  val tunes: TuneLibrary by lazy { TuneLibrary(database) }

  val playlists: PlaylistService by lazy { PlaylistService(database) }

  val exercises: ExerciseLibrary by lazy { ExerciseLibrary(database) }

  val media: MediaService by lazy { MediaService(database) }

  val transcriptions: TranscriptionService by lazy { TranscriptionService(database) }

  val reviewPrompt: ReviewPrompt by lazy { ReviewPrompt(prefs, packageManager.getPackageInfo(packageName, 0).firstInstallTime) }

  /** The times practice was logged, for deciding when to ask for a review. */
  suspend fun practiceTimes(): List<Long> = database.reviewEvents().times()

  val whatsNew: WhatsNew by lazy { WhatsNew(prefs) { assets.open("CHANGELOG.md").bufferedReader().use { it.readText() } } }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  val sessions: SessionRunner by lazy {
    SessionRunner(repository, practice, transcriptions, SessionStore(getSharedPreferences("session", Context.MODE_PRIVATE)), mainScope)
  }

  override fun onCreate() {
    super.onCreate()
    scope.launch { repository.seedIfChanged() }
    SessionNotifications.createChannels(this)
    sessions.restore()
    // The service runs exactly while a session has a block to show.
    mainScope.launch {
      sessions.state.map { it.running && it.step != null }.distinctUntilChanged().collect { active ->
        if (active) startSessionService()
      }
    }
    // A metronome or drone left running for a session stops with it, even if the session was ended from the notification.
    mainScope.launch {
      sessions.state.map { it.running }.distinctUntilChanged().drop(1).collect { running ->
        if (!running) {
          metronome.stop()
          drone.stop()
        }
      }
    }
  }

  private fun startSessionService() {
    try {
      ContextCompat.startForegroundService(this, Intent(this, SessionService::class.java))
    } catch (e: IllegalStateException) {
      // Android refuses a foreground start from the background; the session still runs, and the service
      // starts the next time it changes with the app open.
      Log.w("Woodshed", "could not start the session service", e)
    }
  }
}
