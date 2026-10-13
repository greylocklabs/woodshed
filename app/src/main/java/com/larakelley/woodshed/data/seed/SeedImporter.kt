package com.larakelley.woodshed.data.seed

import androidx.room.withTransaction
import com.larakelley.woodshed.data.freeExerciseName
import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.Performer
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.TranscriptionEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.Tonality

/**
 * Reconciles the database with the seed by natural key (slug, exercise name). Rows keep their ids so
 * anything referencing them survives; rows dropped from the seed are archived, never deleted.
 */
class SeedImporter(private val db: WoodshedDatabase, private val now: () -> Long = System::currentTimeMillis) {
  data class Result(val inserted: Int, val updated: Int, val archived: Int)

  suspend fun import(seed: Seed): Result = db.withTransaction {
    val timestamp = now()
    importStandards(seed.standards, timestamp) + importExercises(seed.exercises, timestamp) + importTranscriptions(seed.transcriptions, timestamp)
  }

  private suspend fun importStandards(seeds: List<StandardSeed>, timestamp: Long): Result {
    val dao = db.standards()
    // The user's own tunes are theirs to change; the seed never updates or archives them.
    val existing = dao.all().filterNot { it.userCreated }.associateBy { it.slug }
    val updates = mutableListOf<StandardEntity>()
    var inserted = 0
    for (s in seeds) {
      val row = StandardEntity(
        id = existing[s.slug]?.id ?: 0,
        slug = s.slug,
        title = s.title,
        composer = s.composer,
        year = s.year,
        bars = s.bars,
        timeSignature = s.timeSignature,
        form = s.form,
        style = s.style,
        keyName = s.key,
        keyPitchClass = Key.pitchClassOf(s.key),
        tonality = Tonality.fromSeedName(s.tonality),
        collection = s.collection,
        verified = s.verified,
      )
      if (row.id == 0L) {
        dao.insert(row)
        inserted++
      } else {
        updates += row
      }
    }
    val seen = seeds.map { it.slug }.toSet()
    val archived = existing.values.filter { it.slug !in seen && it.archivedAt == null }.map { it.copy(archivedAt = timestamp) }
    dao.update(updates + archived)
    return Result(inserted, updates.size, archived.size)
  }

  private suspend fun importExercises(seeds: List<ExerciseSeed>, timestamp: Long): Result {
    val dao = db.exercises()
    // A user's exercise that shares a name with a new seed one keeps its content and gives up the name.
    val seedNames = seeds.map { it.name }.toSet()
    for (mine in dao.all().filter { it.userCreated && it.name in seedNames }) {
      dao.update(listOf(mine.copy(name = freeExerciseName(mine.name, dao.all().map { it.name }.toSet() + seedNames))))
    }
    val existing = dao.all().filterNot { it.userCreated }.associateBy { it.name }
    val updates = mutableListOf<ExerciseEntity>()
    var inserted = 0
    for (e in seeds) {
      val row = ExerciseEntity(
        id = existing[e.name]?.id ?: 0,
        name = e.name,
        category = e.category,
        description = e.description,
        minutes = e.minutes,
        difficulty = e.difficulty,
        tempoBpm = e.tempoBpm,
        families = e.families.map { requireNotNull(Family.fromSeedName(it)) },
        appliesToTune = e.appliesToTune,
      )
      if (row.id == 0L) {
        dao.insert(row)
        inserted++
      } else {
        updates += row
      }
    }
    val seen = seeds.map { it.name }.toSet()
    val archived = existing.values.filter { it.name !in seen && it.archivedAt == null }.map { it.copy(archivedAt = timestamp) }
    dao.update(updates + archived)
    return Result(inserted, updates.size, archived.size)
  }

  /** Runs after the tunes, so each solo can find its tune by slug. */
  private suspend fun importTranscriptions(seeds: List<TranscriptionSeed>, timestamp: Long): Result {
    val dao = db.transcriptions()
    val existing = dao.all().filterNot { it.userCreated }.associateBy { it.slug }
    val tunes = db.standards().all().associate { it.slug to it.id }
    val updates = mutableListOf<TranscriptionEntity>()
    var inserted = 0
    for (t in seeds) {
      val row = TranscriptionEntity(
        id = existing[t.slug]?.id ?: 0,
        slug = t.slug,
        standardId = t.tune?.let { tunes[it] },
        title = t.title,
        soloist = t.soloist,
        instrument = t.instrument,
        context = t.context,
        recorded = t.recorded,
        url = t.url,
        startSeconds = t.startSeconds,
        endSeconds = t.endSeconds,
        keyName = t.key,
        keyPitchClass = Key.pitchClassOf(t.key),
        tonality = Tonality.fromSeedName(t.tonality),
        performers = t.performers.map { Performer(it.name, it.instrument) },
      )
      if (row.id == 0L) {
        dao.insert(row)
        inserted++
      } else {
        updates += row
      }
    }
    val seen = seeds.map { it.slug }.toSet()
    val archived = existing.values.filter { it.slug !in seen && it.archivedAt == null }.map { it.copy(archivedAt = timestamp) }
    dao.update(updates + archived)
    return Result(inserted, updates.size, archived.size)
  }

  private operator fun Result.plus(other: Result) =
    Result(inserted + other.inserted, updated + other.updated, archived + other.archived)
}
