package com.larakelley.woodshed.data

import android.content.SharedPreferences
import android.content.res.AssetManager
import androidx.core.content.edit
import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.seed.Seed
import com.larakelley.woodshed.data.seed.SeedImporter
import com.larakelley.woodshed.domain.Instrument
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WoodshedRepository(
  private val db: WoodshedDatabase,
  private val prefs: SharedPreferences,
  private val assets: AssetManager,
) {
  private val _instrument = MutableStateFlow(prefs.getString(INSTRUMENT, null)?.let { runCatching { Instrument.valueOf(it) }.getOrNull() })

  /** Null until the user picks one; keys then show in concert pitch. */
  val instrument: StateFlow<Instrument?> = _instrument.asStateFlow()

  private val _seedError = MutableStateFlow<String?>(null)
  val seedError: StateFlow<String?> = _seedError.asStateFlow()

  val standards: Flow<List<StandardEntity>> = db.standards().observeActive()
  val exercises: Flow<List<ExerciseEntity>> = db.exercises().observeActive()

  fun setInstrument(instrument: Instrument?) {
    prefs.edit { putString(INSTRUMENT, instrument?.name) }
    _instrument.value = instrument
  }

  /** Imports the bundled seed when it differs from the last one imported. */
  suspend fun seedIfChanged() {
    val standardsJson = assets.open("standards.json").bufferedReader().use { it.readText() }
    val exercisesJson = assets.open("exercises.json").bufferedReader().use { it.readText() }
    val hash = sha256(standardsJson + "\u0000" + exercisesJson)
    if (hash == prefs.getString(SEED_HASH, null)) return
    try {
      SeedImporter(db).import(Seed.parse(standardsJson, exercisesJson))
      prefs.edit { putString(SEED_HASH, hash) }
      _seedError.value = null
    } catch (e: Exception) {
      _seedError.value = e.message ?: e.toString()
    }
  }

  private fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

  private companion object {
    const val INSTRUMENT = "instrument"
    const val SEED_HASH = "seed_hash"
  }
}
