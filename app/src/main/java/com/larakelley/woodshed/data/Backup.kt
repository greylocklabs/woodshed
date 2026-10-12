package com.larakelley.woodshed.data

import androidx.room.withTransaction
import com.larakelley.woodshed.data.db.ReviewEventEntity
import com.larakelley.woodshed.data.db.TuneKnowledgeEntity
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.domain.Comfort
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.KeyPolicy
import com.larakelley.woodshed.domain.Lifecycle
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
data class RestoreResult(val tunes: Int, val knowledge: Int, val reviews: Int, val skipped: Int)

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
      val ids = db.standards().all().associate { it.slug to it.id }
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
      RestoreResult(tunes, knowledge, reviews, skipped)
    }
    repository.applyBackupSettings(backup.settings)
    return result
  }
}
