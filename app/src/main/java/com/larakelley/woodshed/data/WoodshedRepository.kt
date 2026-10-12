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
import com.larakelley.woodshed.domain.Instrument
import com.larakelley.woodshed.domain.Transposition
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light or dark: follow the system, or pick one. */
enum class ThemeMode(val label: String) {
  SYSTEM("System"),
  LIGHT("Light"),
  DARK("Dark"),
}

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

  private val _instrument = MutableStateFlow(enumPref<Instrument>(INSTRUMENT))

  /** What the user plays, or null until they say. Setting it also sets [transposition] and [family]. */
  val instrument: StateFlow<Instrument?> = _instrument.asStateFlow()

  private val _themeMode = MutableStateFlow(enumPref(THEME_MODE) ?: ThemeMode.SYSTEM)
  val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

  private val _lastBackupAt = MutableStateFlow(prefs.getLong(LAST_BACKUP_AT, 0L).takeIf { it > 0 })

  /** When a backup was last exported from this device, or null if never. */
  val lastBackupAt: StateFlow<Long?> = _lastBackupAt.asStateFlow()

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

  fun setInstrument(instrument: Instrument) {
    prefs.edit { putString(INSTRUMENT, instrument.name) }
    _instrument.value = instrument
    setTransposition(instrument.transposition)
    setFamily(instrument.family)
  }

  fun setThemeMode(mode: ThemeMode) {
    prefs.edit { putString(THEME_MODE, mode.name) }
    _themeMode.value = mode
  }

  fun markBackedUp(at: Long) {
    prefs.edit { putLong(LAST_BACKUP_AT, at) }
    _lastBackupAt.value = at
  }

  fun backupSettings() = BackupSettings(
    instrument = instrument.value?.name,
    transposition = transposition.value.name,
    family = family.value?.name,
    themeMode = themeMode.value.name,
    sessionMinutes = sessionMinutes,
    sessionFocus = sessionFocus.map { it.name },
  )

  /** Applies a restored backup's settings, ignoring any value this version does not know. */
  fun applyBackupSettings(s: BackupSettings) {
    Instrument.entries.firstOrNull { it.name == s.instrument }?.let(::setInstrument)
    Transposition.entries.firstOrNull { it.name == s.transposition }?.let(::setTransposition)
    setFamily(Family.entries.firstOrNull { it.name == s.family })
    ThemeMode.entries.firstOrNull { it.name == s.themeMode }?.let(::setThemeMode)
    rememberSession(s.sessionMinutes ?: sessionMinutes, s.sessionFocus.mapNotNull { n -> Focus.entries.firstOrNull { it.name == n } }.toSet())
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
    const val INSTRUMENT = "instrument"
    const val THEME_MODE = "theme_mode"
    const val LAST_BACKUP_AT = "last_backup_at"
    const val SESSION_MINUTES = "session_minutes"
    const val SESSION_FOCUS = "session_focus"
    const val SEED_HASH = "seed_hash"
  }
}
