package com.larakelley.woodshed.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.larakelley.woodshed.domain.Family

/**
 * Author-owned: every column comes from the seed and is refreshed on re-seed. User state for a tune
 * belongs in its own table so re-seeding never touches it.
 */
@Entity(tableName = "standards", indices = [Index(value = ["slug"], unique = true)])
data class StandardEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val slug: String,
  val title: String,
  val composer: String?,
  val year: Int?,
  val bars: Int?,
  val timeSignature: String,
  val form: String?,
  val style: String?,
  /** Exactly as written in the seed, kept even when it does not parse. */
  val commonKeyRaw: String?,
  val commonKeyPitchClass: Int?,
  val commonKeyMinor: Boolean?,
  /** Set when the row disappears from the seed; history may still point at it. */
  val archivedAt: Long? = null,
)

@Entity(tableName = "exercises", indices = [Index(value = ["name"], unique = true)])
data class ExerciseEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val category: String,
  val description: String,
  val minutes: Int,
  val difficulty: Int,
  val tempoBpm: Int?,
  /** Empty means the exercise applies to every family. */
  val families: List<Family>,
  val appliesToTune: Boolean,
  val archivedAt: Long? = null,
)
