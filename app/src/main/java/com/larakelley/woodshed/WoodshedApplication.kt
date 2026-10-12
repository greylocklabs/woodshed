package com.larakelley.woodshed

import android.app.Application
import android.content.Context
import com.larakelley.woodshed.audio.Metronome
import com.larakelley.woodshed.audio.Tuner
import com.larakelley.woodshed.data.BackupService
import com.larakelley.woodshed.data.ExerciseLibrary
import com.larakelley.woodshed.data.MediaService
import com.larakelley.woodshed.data.PlaylistService
import com.larakelley.woodshed.data.PracticeService
import com.larakelley.woodshed.data.TuneLibrary
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.data.db.WoodshedDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WoodshedApplication : Application() {
  private val database by lazy { WoodshedDatabase.create(this) }
  private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }

  val repository: WoodshedRepository by lazy { WoodshedRepository(database, prefs, assets) }

  val metronome: Metronome by lazy { Metronome(prefs) }

  val tuner: Tuner by lazy { Tuner(prefs) }

  val practice: PracticeService by lazy { PracticeService(database) }

  val backups: BackupService by lazy { BackupService(database, repository) }

  val tunes: TuneLibrary by lazy { TuneLibrary(database) }

  val playlists: PlaylistService by lazy { PlaylistService(database) }

  val exercises: ExerciseLibrary by lazy { ExerciseLibrary(database) }

  val media: MediaService by lazy { MediaService(database) }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onCreate() {
    super.onCreate()
    scope.launch { repository.seedIfChanged() }
  }
}
