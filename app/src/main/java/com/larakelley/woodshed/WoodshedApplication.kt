package com.larakelley.woodshed

import android.app.Application
import android.content.Context
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.data.db.WoodshedDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WoodshedApplication : Application() {
  val repository: WoodshedRepository by lazy {
    WoodshedRepository(WoodshedDatabase.create(this), getSharedPreferences("settings", Context.MODE_PRIVATE), assets)
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onCreate() {
    super.onCreate()
    scope.launch { repository.seedIfChanged() }
  }
}
