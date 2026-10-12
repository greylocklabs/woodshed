package com.larakelley.woodshed.data

import androidx.room.withTransaction
import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.ExerciseOverrideEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.db.withOverride
import com.larakelley.woodshed.domain.Family

/** An exercise as the editor fills it in. */
data class ExerciseDraft(
  val name: String = "",
  val category: String = "Technique",
  val description: String = "",
  val minutes: String = "10",
  val difficulty: Int = 2,
  val tempoBpm: String = "",
  /** Empty means every instrument. */
  val families: Set<Family> = emptySet(),
  /** Done to a tune, so it pairs with tunes on cards and in sessions. */
  val appliesToTune: Boolean = false,
) {
  fun problems(taken: (String) -> Boolean = { false }): List<String> = buildList {
    if (name.isBlank()) add("A name is needed.")
    else if (taken(name.trim())) add("Another exercise already has that name.")
    if (description.isBlank()) add("Say how to do it.")
    if (minutes.trim().toIntOrNull()?.takeIf { it in 1..120 } == null) add("Minutes should be a number from 1 to 120.")
    if (tempoBpm.isNotBlank() && tempoBpm.trim().toIntOrNull()?.takeIf { it in 30..300 } == null) add("Tempo should be from 30 to 300 bpm, or blank.")
  }

  companion object {
    fun of(e: ExerciseEntity) = ExerciseDraft(
      name = e.name,
      category = e.category,
      description = e.description,
      minutes = e.minutes.toString(),
      difficulty = e.difficulty,
      tempoBpm = e.tempoBpm?.toString().orEmpty(),
      families = e.families.toSet(),
      appliesToTune = e.appliesToTune,
    )
  }
}

/** Adds, edits and removes exercises on the user's behalf. Seeded exercises are edited through overrides. */
class ExerciseLibrary(private val db: WoodshedDatabase, private val now: () -> Long = System::currentTimeMillis) {
  /** Whether [name] belongs to an active exercise other than [except], as the user sees names. */
  suspend fun nameTaken(name: String, except: Long? = null): Boolean {
    val overrides = db.exerciseOverrides().all().associateBy { it.exerciseId }
    return db.exercises().all().any { it.id != except && it.archivedAt == null && it.withOverride(overrides[it.id]).name.equals(name, ignoreCase = true) }
  }

  suspend fun add(draft: ExerciseDraft): Long = db.withTransaction {
    require(draft.problems { false }.isEmpty())
    require(!nameTaken(draft.name.trim())) { "name taken" }
    db.exercises().insert(entity(draft))
  }

  /** Saves [draft] over exercise [id]: in place for the user's own, as an override of changed fields for seeded ones. */
  suspend fun edit(id: Long, draft: ExerciseDraft) = db.withTransaction {
    require(draft.problems { false }.isEmpty())
    require(!nameTaken(draft.name.trim(), except = id)) { "name taken" }
    val current = db.exercises().byId(id) ?: return@withTransaction
    val wanted = entity(draft)
    if (current.userCreated) {
      db.exercises().update(listOf(wanted.copy(id = id)))
      return@withTransaction
    }
    fun <T> diff(mine: T?, seed: T?): T? = mine?.takeIf { it != seed }
    val o = ExerciseOverrideEntity(
      exerciseId = id,
      name = diff(wanted.name, current.name),
      category = diff(wanted.category, current.category),
      description = diff(wanted.description, current.description),
      minutes = diff(wanted.minutes, current.minutes),
      difficulty = diff(wanted.difficulty, current.difficulty),
      tempoBpm = diff(wanted.tempoBpm, current.tempoBpm),
      families = wanted.families.takeIf { it.toSet() != current.families.toSet() },
      appliesToTune = diff(wanted.appliesToTune, current.appliesToTune),
      updatedAt = now(),
    )
    if (o == ExerciseOverrideEntity(id, updatedAt = o.updatedAt)) db.exerciseOverrides().delete(id) else db.exerciseOverrides().upsert(o)
  }

  suspend fun resetToOriginal(id: Long) = db.exerciseOverrides().delete(id)

  /** Archives one of the user's own exercises, so history that used it keeps its name. */
  suspend fun delete(id: Long) {
    val e = db.exercises().byId(id)?.takeIf { it.userCreated } ?: return
    db.exercises().update(listOf(e.copy(archivedAt = now())))
  }

  private fun entity(d: ExerciseDraft) = ExerciseEntity(
    name = d.name.trim(),
    category = d.category,
    description = d.description.trim(),
    minutes = d.minutes.trim().toInt(),
    difficulty = d.difficulty.coerceIn(1, 5),
    tempoBpm = d.tempoBpm.trim().toIntOrNull(),
    families = Family.entries.filter { it in d.families },
    appliesToTune = d.appliesToTune,
    userCreated = true,
  )
}
