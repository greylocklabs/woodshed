package com.larakelley.woodshed.data

import android.content.SharedPreferences
import android.content.res.AssetManager
import androidx.core.content.edit
import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.TuneKnowledgeEntity
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.seed.Seed
import com.larakelley.woodshed.data.seed.SeedImporter
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Focus
import com.larakelley.woodshed.domain.Transposition
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
  private val _transposition = MutableStateFlow(enumPref(TRANSPOSITION) ?: Transposition.C)
  val transposition: StateFlow<Transposition> = _transposition.asStateFlow()

  private val _family = MutableStateFlow(enumPref<Family>(FAMILY))

  /** Null shows exercises for every family. */
  val family: StateFlow<Family?> = _family.asStateFlow()

  /** The last session length and focus, offered again next time. */
  val sessionMinutes: Int
    get() = prefs.getInt(SESSION_MINUTES, 30)
  val sessionFocus: Set<Focus>
    get() = prefs.getStringSet(SESSION_FOCUS, emptySet())!!.mapNotNull { name -> Focus.entries.firstOrNull { it.name == name } }.toSet()

  private val _seedError = MutableStateFlow<String?>(null)
  val seedError: StateFlow<String?> = _seedError.asStateFlow()

  val standards: Flow<List<StandardEntity>> = db.standards().observeActive()
  val exercises: Flow<List<ExerciseEntity>> = db.exercises().observeActive()
  val tuneStates: Flow<List<TuneStateEntity>> = db.tuneStates().observeAll()
  val tuneKnowledge: Flow<List<TuneKnowledgeEntity>> = db.tuneKnowledge().observeAll()

  fun setTransposition(transposition: Transposition) {
    prefs.edit { putString(TRANSPOSITION, transposition.name) }
    _transposition.value = transposition
  }

  fun setFamily(family: Family?) {
    prefs.edit { putString(FAMILY, family?.name) }
    _family.value = family
  }

  fun rememberSession(minutes: Int, focus: Set<Focus>) {
    prefs.edit {
      putInt(SESSION_MINUTES, minutes)
      putStringSet(SESSION_FOCUS, focus.map { it.name }.toSet())
    }
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

  private inline fun <reified T : Enum<T>> enumPref(key: String): T? =
    prefs.getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } }

  private fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

  private companion object {
    const val TRANSPOSITION = "transposition"
    const val FAMILY = "family"
    const val SESSION_MINUTES = "session_minutes"
    const val SESSION_FOCUS = "session_focus"
    const val SEED_HASH = "seed_hash"
  }
}
