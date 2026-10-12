package com.larakelley.woodshed.data

import androidx.room.withTransaction
import com.larakelley.woodshed.data.db.PlaylistEntity
import com.larakelley.woodshed.data.db.PlaylistTuneEntity
import com.larakelley.woodshed.data.db.ReviewEventEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.TuneKnowledgeEntity
import com.larakelley.woodshed.data.db.TuneOverrideEntity
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.domain.Comfort
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.KeyPolicy
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.Tonality
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Everything the user has made, keyed by tune slug and exercise name rather than database ids, so a backup
 * restores into any install whatever order the seed was imported in. Seed data is not included; it ships with
 * the app.
 */
@Serializable
data class Backup(
  val format: String = FORMAT,
  val version: Int = VERSION,
  @SerialName("exported_at") val exportedAt: Long,
  val settings: BackupSettings,
  /** Tunes the user added, restored before anything that refers to them. */
  @SerialName("my_tunes") val myTunes: List<BackupMyTune> = emptyList(),
  /** The user's edits to seeded tunes. */
  val edits: List<BackupEdit> = emptyList(),
  /** Playlists, with tunes named by slug in order. */
  val playlists: List<BackupPlaylist> = emptyList(),
  val tunes: List<BackupTune>,
  val knowledge: List<BackupKnowledge>,
  val reviews: List<BackupReview>,
) {
  companion object {
    const val FORMAT = "woodshed-backup"
    const val VERSION = 1
  }
}

@Serializable
data class BackupSettings(
  val instrument: String? = null,
  @SerialName("my_instruments") val myInstruments: List<String> = emptyList(),
  val transposition: String? = null,
  @SerialName("theme_mode") val themeMode: String? = null,
  @SerialName("session_minutes") val sessionMinutes: Int? = null,
  @SerialName("session_focus") val sessionFocus: List<String> = emptyList(),
)

@Serializable
data class BackupMyTune(
  val slug: String,
  val title: String,
  val composer: String? = null,
  val year: Int? = null,
  val bars: Int? = null,
  @SerialName("time_signature") val timeSignature: String? = null,
  val form: String? = null,
  val style: String? = null,
  val key: String? = null,
  val tonality: Tonality? = null,
  val collection: String = "Standards",
)

@Serializable
data class BackupPlaylist(val name: String, val tunes: List<String>, @SerialName("created_at") val createdAt: Long = 0)

@Serializable
data class BackupEdit(
  val slug: String,
  val title: String? = null,
  val composer: String? = null,
  val year: Int? = null,
  val bars: Int? = null,
  @SerialName("time_signature") val timeSignature: String? = null,
  val form: String? = null,
  val style: String? = null,
  val key: String? = null,
  val tonality: Tonality? = null,
  val collection: String? = null,
  @SerialName("updated_at") val updatedAt: Long = 0,
)

@Serializable
data class BackupTune(
  val slug: String,
  val lifecycle: Lifecycle,
  @SerialName("key_policy") val keyPolicy: KeyPolicy,
  @SerialName("cycle_position") val cyclePosition: Int,
  val notes: String? = null,
  @SerialName("wishlisted_at") val wishlistedAt: Long? = null,
  @SerialName("introduced_at") val introducedAt: Long? = null,
  val reps: Int,
  val lapses: Int,
  @SerialName("interval_days") val intervalDays: Double,
  val ease: Double,
  @SerialName("due_at") val dueAt: Long? = null,
  @SerialName("last_reviewed_at") val lastReviewedAt: Long? = null,
)

@Serializable
data class BackupKnowledge(
  val slug: String,
  val comfort: Comfort? = null,
  @SerialName("known_keys") val knownKeys: Int,
  @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class BackupReview(
  val slug: String,
  val exercise: String? = null,
  val transposition: String,
  @SerialName("key_written_pitch_class") val keyWrittenPitchClass: Int? = null,
  @SerialName("key_written_minor") val keyWrittenMinor: Boolean? = null,
  val grade: Grade,
  @SerialName("reviewed_at") val reviewedAt: Long,
  @SerialName("elapsed_ms") val elapsedMs: Long,
  @SerialName("scheduler_id") val schedulerId: String,
  @SerialName("scheduler_version") val schedulerVersion: Int,
  @SerialName("interval_before") val intervalBefore: Double,
  @SerialName("interval_after") val intervalAfter: Double,
  @SerialName("ease_before") val easeBefore: Double,
  @SerialName("ease_after") val easeAfter: Double,
)

/** What a restore brought back, and how many rows named tunes this install does not have. */
data class RestoreResult(
  val tunes: Int,
  val knowledge: Int,
  val reviews: Int,
  val skipped: Int,
  val myTunes: Int = 0,
  val edits: Int = 0,
  val playlists: Int = 0,
)

class BackupException(message: String) : Exception(message)

class BackupService(
  private val db: WoodshedDatabase,
  private val repository: WoodshedRepository,
  private val now: () -> Long = System::currentTimeMillis,
) {
  private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    // The format and version markers are defaults, and a backup is not recognisable without them.
    encodeDefaults = true
  }

  suspend fun export(): String {
    val slugs = db.standards().all().associate { it.id to it.slug }
    val exercises = db.exercises().all().associate { it.id to it.name }
    val backup = Backup(
      exportedAt = now(),
      settings = repository.backupSettings(),
      myTunes = db.standards().userCreated().filter { it.archivedAt == null }.map {
        BackupMyTune(it.slug, it.title, it.composer, it.year, it.bars, it.timeSignature, it.form, it.style, it.keyName, it.tonality, it.collection)
      },
      playlists = db.playlists().all().let { lists ->
        val created = lists.associate { it.id to it.createdAt }
        playlists(lists, db.playlists().entries()).map { p -> BackupPlaylist(p.name, p.tuneIds.mapNotNull { slugs[it] }, created.getValue(p.id)) }
      },
      edits = db.tuneOverrides().all().mapNotNull { o ->
        val slug = slugs[o.standardId] ?: return@mapNotNull null
        BackupEdit(slug, o.title, o.composer, o.year, o.bars, o.timeSignature, o.form, o.style, o.keyName, o.tonality, o.collection, o.updatedAt)
      },
      tunes = db.tuneStates().all().mapNotNull { s ->
        val slug = slugs[s.standardId] ?: return@mapNotNull null
        BackupTune(slug, s.lifecycle, s.keyPolicy, s.cyclePosition, s.notes, s.wishlistedAt, s.introducedAt, s.reps, s.lapses, s.intervalDays, s.ease, s.dueAt, s.lastReviewedAt)
      },
      knowledge = db.tuneKnowledge().all().mapNotNull { k ->
        slugs[k.standardId]?.let { BackupKnowledge(it, k.comfort, k.knownKeys, k.updatedAt) }
      },
      reviews = db.reviewEvents().all().filter { it.itemKind == ReviewEventEntity.TUNE }.mapNotNull { e ->
        val slug = slugs[e.itemId] ?: return@mapNotNull null
        BackupReview(
          slug, e.exerciseId?.let { exercises[it] }, e.transposition, e.keyWrittenPitchClass, e.keyWrittenMinor, e.grade, e.reviewedAt,
          e.elapsedMs, e.schedulerId, e.schedulerVersion, e.intervalBefore, e.intervalAfter, e.easeBefore, e.easeAfter,
        )
      },
    )
    return json.encodeToString(Backup.serializer(), backup)
  }

  /** Replaces the user's tunes, knowledge and history with the backup's. Throws [BackupException] if [text] is not a backup. */
  suspend fun restore(text: String): RestoreResult {
    val backup = try {
      val marker = json.parseToJsonElement(text).jsonObject["format"]?.jsonPrimitive?.content
      if (marker != Backup.FORMAT) throw BackupException("This file is not a Woodshed backup.")
      json.decodeFromString(Backup.serializer(), text)
    } catch (e: IllegalArgumentException) {
      throw BackupException("This file is not a Woodshed backup.")
    }
    if (backup.version > Backup.VERSION) throw BackupException("This backup is from a newer version of Woodshed.")

    val result = db.withTransaction {
      // The user's own tunes first, so state and history below can find them. Ones not in the backup are archived.
      val keep = backup.myTunes.map { it.slug }.toSet()
      for (old in db.standards().userCreated()) {
        if (old.slug !in keep && old.archivedAt == null) db.standards().update(listOf(old.copy(archivedAt = now())))
      }
      for (t in backup.myTunes) {
        val row = StandardEntity(
          slug = t.slug, title = t.title, composer = t.composer, year = t.year, bars = t.bars, timeSignature = t.timeSignature, form = t.form,
          style = t.style, keyName = t.key, keyPitchClass = Key.pitchClassOf(t.key), tonality = t.tonality, collection = t.collection, userCreated = true,
        )
        val existing = db.standards().bySlug(t.slug)
        if (existing == null) db.standards().insert(row) else db.standards().update(listOf(row.copy(id = existing.id)))
      }
      val ids = db.standards().all().associate { it.slug to it.id }
      db.tuneOverrides().deleteAll()
      var edits = 0
      for (e in backup.edits) {
        val id = ids[e.slug] ?: continue
        db.tuneOverrides().upsert(
          TuneOverrideEntity(id, e.title, e.composer, e.year, e.bars, e.timeSignature, e.form, e.style, e.key, Key.pitchClassOf(e.key), e.tonality, e.collection, e.updatedAt)
        )
        edits++
      }
      val exercises = db.exercises().all().associate { it.name to it.id }
      db.reviewEvents().deleteAll()
      db.tuneKnowledge().deleteAll()
      db.tuneStates().deleteAll()
      var skipped = 0
      var tunes = 0
      for (t in backup.tunes) {
        val id = ids[t.slug]
        if (id == null) {
          skipped++
          continue
        }
        db.tuneStates().upsert(
          TuneStateEntity(id, t.lifecycle, t.keyPolicy, t.cyclePosition, t.notes, t.wishlistedAt, t.introducedAt, t.reps, t.lapses, t.intervalDays, t.ease, t.dueAt, t.lastReviewedAt)
        )
        tunes++
      }
      var knowledge = 0
      for (k in backup.knowledge) {
        val id = ids[k.slug]
        if (id == null) {
          skipped++
          continue
        }
        db.tuneKnowledge().upsert(TuneKnowledgeEntity(id, k.comfort, k.knownKeys, k.updatedAt))
        knowledge++
      }
      var reviews = 0
      for (r in backup.reviews) {
        val id = ids[r.slug]
        if (id == null) {
          skipped++
          continue
        }
        db.reviewEvents().insert(
          ReviewEventEntity(
            itemKind = ReviewEventEntity.TUNE, itemId = id, exerciseId = r.exercise?.let { exercises[it] }, transposition = r.transposition,
            keyWrittenPitchClass = r.keyWrittenPitchClass, keyWrittenMinor = r.keyWrittenMinor, grade = r.grade, reviewedAt = r.reviewedAt,
            elapsedMs = r.elapsedMs, schedulerId = r.schedulerId, schedulerVersion = r.schedulerVersion, intervalBefore = r.intervalBefore,
            intervalAfter = r.intervalAfter, easeBefore = r.easeBefore, easeAfter = r.easeAfter,
          )
        )
        reviews++
      }
      db.playlists().deleteAll()
      for (p in backup.playlists) {
        val id = db.playlists().insert(PlaylistEntity(name = p.name, createdAt = p.createdAt))
        val order = p.tunes.mapNotNull { ids[it] }.distinct()
        db.playlists().upsertEntries(order.mapIndexed { i, tune -> PlaylistTuneEntity(id, tune, i) })
      }
      RestoreResult(tunes, knowledge, reviews, skipped, backup.myTunes.size, edits, backup.playlists.size)
    }
    repository.applyBackupSettings(backup.settings)
    return result
  }
}
