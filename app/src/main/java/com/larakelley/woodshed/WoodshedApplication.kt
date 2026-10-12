package com.larakelley.woodshed

import android.app.Application
import android.content.Context
import com.larakelley.woodshed.audio.Metronome
import com.larakelley.woodshed.data.BackupService
import com.larakelley.woodshed.data.PracticeService
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

  val practice: PracticeService by lazy { PracticeService(database) }

  val backups: BackupService by lazy { BackupService(database, repository) }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onCreate() {
    super.onCreate()
    scope.launch { repository.seedIfChanged() }
  }
}
