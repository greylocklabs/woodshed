package com.larakelley.woodshed.data

import androidx.room.withTransaction
import com.larakelley.woodshed.data.db.BlockOutcome
import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.ExerciseOverrideEntity
import com.larakelley.woodshed.data.db.Performer
import com.larakelley.woodshed.data.db.PlaylistEntity
import com.larakelley.woodshed.data.db.PlaylistTuneEntity
import com.larakelley.woodshed.data.db.ReviewEventEntity
import com.larakelley.woodshed.data.db.SessionBlockEntity
import com.larakelley.woodshed.data.db.SessionEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.TranscriptionEntity
import com.larakelley.woodshed.data.db.TranscriptionStateEntity
import com.larakelley.woodshed.data.db.TranscriptionStatus
import com.larakelley.woodshed.data.db.TuneKnowledgeEntity
import com.larakelley.woodshed.data.db.TuneMediaEntity
import com.larakelley.woodshed.data.db.TuneOverrideEntity
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.db.clearedFields
import com.larakelley.woodshed.data.seed.SeedValidator
import com.larakelley.woodshed.domain.Comfort
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.KeyPolicy
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.MediaLink
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
  /** Exercises the user added, restored before history that refers to them by name. */
  @SerialName("my_exercises") val myExercises: List<BackupExercise> = emptyList(),
  /** The user's edits to seeded exercises, keyed by the seed's name. */
  @SerialName("exercise_edits") val exerciseEdits: List<BackupExerciseEdit> = emptyList(),
  /** Solos the user added, by slug; their progress is in [soloProgress] with everyone else's. */
  @SerialName("my_solos") val mySolos: List<BackupSolo> = emptyList(),
  /** Progress, notes and scheduling on solos, by the solo's slug. */
  @SerialName("solo_progress") val soloProgress: List<BackupSoloProgress> = emptyList(),
  /** Recording and video links on tunes, named by slug. */
  val media: List<BackupMedia> = emptyList(),
  /** Playlists, with tunes named by slug in order. */
  val playlists: List<BackupPlaylist> = emptyList(),
  val tunes: List<BackupTune>,
  val knowledge: List<BackupKnowledge>,
  val reviews: List<BackupReview>,
  /** Practice sessions, newest first, with their blocks naming tunes and solos by slug and exercises by name. */
  val sessions: List<BackupSession> = emptyList(),
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
  /** The main tabs in the user's order, by name; empty for the default. */
  @SerialName("tab_order") val tabOrder: List<String> = emptyList(),
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
data class BackupExercise(
  val name: String,
  val category: String,
  val description: String,
  val minutes: Int,
  val difficulty: Int,
  @SerialName("tempo_bpm") val tempoBpm: Int? = null,
  val families: List<Family> = emptyList(),
  @SerialName("applies_to_tune") val appliesToTune: Boolean = false,
)

@Serializable
data class BackupExerciseEdit(
  val exercise: String,
  val name: String? = null,
  val category: String? = null,
  val description: String? = null,
  val minutes: Int? = null,
  val difficulty: Int? = null,
  @SerialName("tempo_bpm") val tempoBpm: Int? = null,
  val families: List<Family>? = null,
  @SerialName("applies_to_tune") val appliesToTune: Boolean? = null,
  @SerialName("updated_at") val updatedAt: Long = 0,
  val cleared: List<String> = emptyList(),
)

@Serializable
data class BackupSolo(
  val slug: String,
  val tune: String? = null,
  val title: String,
  val soloist: String,
  val instrument: String,
  val context: String? = null,
  val recorded: String? = null,
  val url: String,
  @SerialName("start_seconds") val startSeconds: Int? = null,
  @SerialName("end_seconds") val endSeconds: Int? = null,
  val key: String? = null,
  val tonality: Tonality? = null,
  val performers: List<Performer> = emptyList(),
)

@Serializable
data class BackupSoloProgress(
  val slug: String,
  val status: TranscriptionStatus,
  val notes: String? = null,
  @SerialName("started_at") val startedAt: Long? = null,
  @SerialName("done_at") val doneAt: Long? = null,
  val reps: Int = 0,
  val lapses: Int = 0,
  @SerialName("interval_days") val intervalDays: Double = 0.0,
  val ease: Double = 2.5,
  @SerialName("due_at") val dueAt: Long? = null,
  @SerialName("last_reviewed_at") val lastReviewedAt: Long? = null,
)

@Serializable
data class BackupMedia(val slug: String, val url: String, val title: String? = null, val position: Int = 0, @SerialName("created_at") val createdAt: Long = 0)

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
  /** Fields the user blanked. */
  val cleared: List<String> = emptyList(),
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
  /** What [slug] names: [KIND_TUNE] or [KIND_SOLO]. */
  val kind: String = KIND_TUNE,
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
) {
  companion object {
    const val KIND_TUNE = "tune"
    const val KIND_SOLO = "solo"
  }
}

@Serializable
data class BackupSession(
  @SerialName("started_at") val startedAt: Long,
  @SerialName("ended_at") val endedAt: Long? = null,
  @SerialName("planned_minutes") val plannedMinutes: Int,
  val instrument: String? = null,
  val completed: Boolean = false,
  val blocks: List<BackupSessionBlock> = emptyList(),
)

@Serializable
data class BackupSessionBlock(
  val position: Int,
  /** [SessionBlockEntity.KIND_EXERCISE], [SessionBlockEntity.KIND_TUNE] or [SessionBlockEntity.KIND_SOLO]. */
  val kind: String,
  /** The exercise's name, or the tune's or solo's slug. */
  val item: String,
  /** A tune block's exercise, by name. */
  val exercise: String? = null,
  @SerialName("planned_minutes") val plannedMinutes: Int,
  val outcome: BlockOutcome? = null,
  val grade: Grade? = null,
  @SerialName("elapsed_ms") val elapsedMs: Long = 0,
  @SerialName("finished_at") val finishedAt: Long? = null,
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

class BackupService(private val db: WoodshedDatabase, private val repository: WoodshedRepository, private val now: () -> Long = System::currentTimeMillis) {
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
      myExercises = db.exercises().userCreated().filter { it.archivedAt == null }.map {
        BackupExercise(it.name, it.category, it.description, it.minutes, it.difficulty, it.tempoBpm, it.families, it.appliesToTune)
      },
      exerciseEdits = db.exerciseOverrides().all().mapNotNull { o ->
        val name = exercises[o.exerciseId] ?: return@mapNotNull null
        BackupExerciseEdit(
          name, o.name, o.category, o.description, o.minutes, o.difficulty, o.tempoBpm, o.families, o.appliesToTune, o.updatedAt,
          o.cleared.splitFields(),
        )
      },
      mySolos = db.transcriptions().all().filter { it.userCreated && it.archivedAt == null }.map {
        BackupSolo(
          it.slug,
          it.standardId?.let { s ->
            slugs[s]
          },
          it.title, it.soloist, it.instrument, it.context, it.recorded, it.url, it.startSeconds, it.endSeconds, it.keyName, it.tonality, it.performers,
        )
      },
      soloProgress = db.transcriptions().all().associate { it.id to it.slug }.let { soloSlugs ->
        db.transcriptions().states().mapNotNull { s ->
          soloSlugs[s.transcriptionId]?.let {
            BackupSoloProgress(it, s.status, s.notes, s.startedAt, s.doneAt, s.reps, s.lapses, s.intervalDays, s.ease, s.dueAt, s.lastReviewedAt)
          }
        }
      },
      media = db.tuneMedia().all().mapNotNull { m -> slugs[m.standardId]?.let { BackupMedia(it, m.url, m.title, m.position, m.createdAt) } },
      playlists = db.playlists().all().let { lists ->
        val created = lists.associate { it.id to it.createdAt }
        playlists(lists, db.playlists().entries()).map { p -> BackupPlaylist(p.name, p.tuneIds.mapNotNull { slugs[it] }, created.getValue(p.id)) }
      },
      edits = db.tuneOverrides().all().mapNotNull { o ->
        val slug = slugs[o.standardId] ?: return@mapNotNull null
        BackupEdit(
          slug, o.title, o.composer, o.year, o.bars, o.timeSignature, o.form, o.style, o.keyName, o.tonality, o.collection, o.updatedAt,
          o.cleared.splitFields(),
        )
      },
      tunes = db.tuneStates().all().mapNotNull { s ->
        val slug = slugs[s.standardId] ?: return@mapNotNull null
        BackupTune(
          slug, s.lifecycle, s.keyPolicy, s.cyclePosition, s.notes, s.wishlistedAt, s.introducedAt, s.reps, s.lapses, s.intervalDays, s.ease, s.dueAt,
          s.lastReviewedAt,
        )
      },
      sessions = exportSessions(slugs, exercises),
      knowledge = db.tuneKnowledge().all().mapNotNull { k ->
        slugs[k.standardId]?.let { BackupKnowledge(it, k.comfort, k.knownKeys, k.updatedAt) }
      },
      reviews = db.transcriptions().all().associate { it.id to it.slug }.let { soloSlugs ->
        db.reviewEvents().all().mapNotNull { e ->
          val (slug, kind) = when (e.itemKind) {
            ReviewEventEntity.TUNE -> slugs[e.itemId] to BackupReview.KIND_TUNE
            ReviewEventEntity.TRANSCRIPTION -> soloSlugs[e.itemId] to BackupReview.KIND_SOLO
            else -> null to ""
          }
          if (slug == null) return@mapNotNull null
          BackupReview(
            slug, kind, e.exerciseId?.let { exercises[it] }, e.transposition, e.keyWrittenPitchClass, e.keyWrittenMinor, e.grade, e.reviewedAt,
            e.elapsedMs, e.schedulerId, e.schedulerVersion, e.intervalBefore, e.intervalAfter, e.easeBefore, e.easeAfter,
          )
        }
      },
    )
    return json.encodeToString(Backup.serializer(), backup)
  }

  private suspend fun exportSessions(slugs: Map<Long, String>, exercises: Map<Long, String>): List<BackupSession> {
    val solos = db.transcriptions().all().associate { it.id to it.slug }
    val blocks = db.sessions().allBlocks().groupBy { it.sessionId }
    return db.sessions().all().map { s ->
      BackupSession(
        s.startedAt,
        s.endedAt,
        s.plannedMinutes,
        s.instrument,
        s.completed,
        blocks[s.id].orEmpty().mapNotNull { b ->
          val item = when (b.kind) {
            SessionBlockEntity.KIND_EXERCISE -> exercises[b.itemId]
            SessionBlockEntity.KIND_TUNE -> slugs[b.itemId]
            else -> solos[b.itemId]
          } ?: return@mapNotNull null
          BackupSessionBlock(b.position, b.kind, item, b.exerciseId?.let { exercises[it] }, b.plannedMinutes, b.outcome, b.grade, b.elapsedMs, b.finishedAt)
        },
      )
    }
  }

  /** Brings back [sessions], dropping blocks whose tune, solo or exercise this install does not have. */
  private suspend fun restoreSessions(sessions: List<BackupSession>, tunes: Map<String, Long>, solos: Map<String, Long>, exercises: Map<String, Long>) {
    db.sessions().deleteAll()
    for (s in sessions) {
      val id = db.sessions().insert(
        SessionEntity(startedAt = s.startedAt, endedAt = s.endedAt, plannedMinutes = s.plannedMinutes, instrument = s.instrument, completed = s.completed),
      )
      db.sessions().insertBlocks(
        s.blocks.mapNotNull { b ->
          val item = when (b.kind) {
            SessionBlockEntity.KIND_EXERCISE -> exercises[b.item]
            SessionBlockEntity.KIND_TUNE -> tunes[b.item]
            SessionBlockEntity.KIND_SOLO -> solos[b.item]
            else -> null
          } ?: return@mapNotNull null
          SessionBlockEntity(id, b.position, b.kind, item, b.exercise?.let { exercises[it] }, b.plannedMinutes, b.outcome, b.grade, b.elapsedMs, b.finishedAt)
        },
      )
    }
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
      // Only the user's own slugs: anything else would take over a seeded tune that re-seeding then never updates.
      for (t in backup.myTunes.filter { it.slug.startsWith(SeedValidator.USER_SLUG_PREFIX) }) {
        val row = StandardEntity(
          slug = t.slug, title = t.title, composer = t.composer, year = t.year, bars = t.bars, timeSignature = t.timeSignature, form = t.form,
          style = t.style, keyName = t.key, keyPitchClass = Key.pitchClassOf(t.key), tonality = t.tonality, collection = t.collection, userCreated = true,
        )
        val existing = db.standards().bySlug(t.slug)
        if (existing == null) {
          db.standards().insert(row)
        } else if (existing.userCreated) {
          db.standards().update(listOf(row.copy(id = existing.id)))
        }
      }
      val keepExercises = backup.myExercises.map { it.name }.toSet()
      for (old in db.exercises().userCreated()) {
        if (old.name !in keepExercises && old.archivedAt == null) db.exercises().update(listOf(old.copy(archivedAt = now())))
      }
      for (e in backup.myExercises) {
        val row = ExerciseEntity(
          name = e.name, category = e.category, description = e.description, minutes = e.minutes, difficulty = e.difficulty,
          tempoBpm = e.tempoBpm, families = e.families, appliesToTune = e.appliesToTune, userCreated = true,
        )
        val existing = db.exercises().byName(e.name)
        // A seeded exercise with the same name keeps it; the restored one is marked as the user's.
        when {
          existing == null -> db.exercises().insert(row)

          existing.userCreated -> db.exercises().update(listOf(row.copy(id = existing.id)))

          else -> {
            // Restoring the same file again finds the copy made last time rather than making another.
            val earlier = db.exercises().byName("${e.name} (yours)")?.takeIf { it.userCreated }
            if (earlier != null) {
              db.exercises().update(listOf(row.copy(id = earlier.id, name = earlier.name)))
            } else {
              db.exercises().insert(row.copy(name = freeExerciseName(e.name, db.exercises().all().map { it.name }.toSet())))
            }
          }
        }
      }
      db.exerciseOverrides().deleteAll()
      val exerciseIds = db.exercises().all().associate { it.name to it.id }
      for (e in backup.exerciseEdits) {
        val id = exerciseIds[e.exercise] ?: continue
        db.exerciseOverrides().upsert(
          ExerciseOverrideEntity(
            id, e.name, e.category, e.description, e.minutes, e.difficulty, e.tempoBpm, e.families, e.appliesToTune, e.updatedAt,
            clearedFields(
              e.cleared,
            ),
          ),
        )
      }
      val ids = db.standards().all().associate { it.slug to it.id }
      db.tuneOverrides().deleteAll()
      var edits = 0
      for (e in backup.edits) {
        val id = ids[e.slug] ?: continue
        db.tuneOverrides().upsert(
          TuneOverrideEntity(
            id, e.title, e.composer, e.year, e.bars, e.timeSignature, e.form, e.style, e.key,
            Key.pitchClassOf(
              e.key,
            ),
            e.tonality, e.collection, e.updatedAt, clearedFields(e.cleared),
          ),
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
          TuneStateEntity(
            id, t.lifecycle, t.keyPolicy, t.cyclePosition, t.notes, t.wishlistedAt, t.introducedAt, t.reps, t.lapses, t.intervalDays, t.ease, t.dueAt,
            t.lastReviewedAt,
          ),
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
      for (r in backup.reviews.filter { it.kind == BackupReview.KIND_TUNE }) {
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
          ),
        )
        reviews++
      }
      // Solos: the user's own first, then progress on any solo, matched by slug.
      val keepSolos = backup.mySolos.map { it.slug }.toSet()
      for (old in db.transcriptions().all().filter { it.userCreated && it.slug !in keepSolos && it.archivedAt == null }) {
        db.transcriptions().update(listOf(old.copy(archivedAt = now())))
      }
      for (s in backup.mySolos.filter { it.slug.startsWith(SeedValidator.USER_SLUG_PREFIX) }) {
        val row = TranscriptionEntity(
          slug = s.slug,
          standardId = s.tune?.let {
            ids[it]
          },
          title = s.title, soloist = s.soloist, instrument = s.instrument, context = s.context, recorded = s.recorded, url = s.url,
          startSeconds = s.startSeconds, endSeconds = s.endSeconds, keyName = s.key, keyPitchClass = Key.pitchClassOf(s.key), tonality = s.tonality,
          performers = s.performers, userCreated = true,
        )
        val existing = db.transcriptions().bySlug(s.slug)
        if (existing == null) {
          db.transcriptions().insert(row)
        } else if (existing.userCreated) {
          db.transcriptions().update(listOf(row.copy(id = existing.id)))
        }
      }
      db.transcriptions().deleteAllStates()
      val soloIds = db.transcriptions().all().associate { it.slug to it.id }
      for (p in backup.soloProgress) {
        val id = soloIds[p.slug] ?: continue
        db.transcriptions().upsertState(
          TranscriptionStateEntity(id, p.status, p.notes, p.startedAt, p.doneAt, p.reps, p.lapses, p.intervalDays, p.ease, p.dueAt, p.lastReviewedAt),
        )
      }
      for (r in backup.reviews.filter { it.kind == BackupReview.KIND_SOLO }) {
        val id = soloIds[r.slug] ?: continue
        db.reviewEvents().insert(
          ReviewEventEntity(
            itemKind = ReviewEventEntity.TRANSCRIPTION, itemId = id, exerciseId = null, transposition = r.transposition,
            keyWrittenPitchClass = r.keyWrittenPitchClass, keyWrittenMinor = r.keyWrittenMinor, grade = r.grade, reviewedAt = r.reviewedAt,
            elapsedMs = r.elapsedMs, schedulerId = r.schedulerId, schedulerVersion = r.schedulerVersion, intervalBefore = r.intervalBefore,
            intervalAfter = r.intervalAfter, easeBefore = r.easeBefore, easeAfter = r.easeAfter,
          ),
        )
        reviews++
      }
      db.tuneMedia().deleteAll()
      for (m in backup.media) {
        val id = ids[m.slug] ?: continue
        // Opening a link that is not a web address would crash, so the restore drops it as adding it would.
        if (MediaLink.parse(m.url) == null) continue
        db.tuneMedia().insert(TuneMediaEntity(standardId = id, url = m.url, title = m.title, position = m.position, createdAt = m.createdAt))
      }
      db.playlists().deleteAll()
      for (p in backup.playlists) {
        val id = db.playlists().insert(PlaylistEntity(name = p.name, createdAt = p.createdAt))
        val order = p.tunes.mapNotNull { ids[it] }.distinct()
        db.playlists().upsertEntries(order.mapIndexed { i, tune -> PlaylistTuneEntity(id, tune, i) })
      }
      restoreSessions(backup.sessions, ids, soloIds, exercises)
      RestoreResult(tunes, knowledge, reviews, skipped, backup.myTunes.size, edits, backup.playlists.size)
    }
    repository.applyBackupSettings(backup.settings)
    return result
  }
}

private fun String.splitFields(): List<String> = split(',').filter { it.isNotEmpty() }
