package com.larakelley.woodshed

import android.app.Application
import android.content.Context
import com.larakelley.woodshed.data.PracticeService
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.data.db.WoodshedDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WoodshedApplication : Application() {
  private val database by lazy { WoodshedDatabase.create(this) }

  val repository: WoodshedRepository by lazy {
    WoodshedRepository(database, getSharedPreferences("settings", Context.MODE_PRIVATE), assets)
  }

  val practice: PracticeService by lazy { PracticeService(database) }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onCreate() {
    super.onCreate()
    scope.launch { repository.seedIfChanged() }
  }
}
