package com.larakelley.woodshed.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.larakelley.woodshed.domain.CardState
import com.larakelley.woodshed.domain.Comfort
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.KeyPolicy
import com.larakelley.woodshed.domain.Lifecycle

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
  val timeSignature: String?,
  val form: String?,
  val style: String?,
  /** Exactly as written in the seed, kept even when it does not parse. */
  val commonKeyRaw: String?,
  val commonKeyPitchClass: Int?,
  val commonKeyMinor: Boolean?,
  /** Set when the row disappears from the seed; history may still point at it. */
  val archivedAt: Long? = null,
  @ColumnInfo(defaultValue = "Standards") val collection: String = "Standards",
  @ColumnInfo(defaultValue = "1") val verified: Boolean = true,
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

/**
 * User-owned state for a tune; re-seeding never touches it. The scheduling columns are a cache that can be
 * rebuilt from review_events.
 */
@Entity(
  tableName = "tune_state",
  foreignKeys = [ForeignKey(entity = StandardEntity::class, parentColumns = ["id"], childColumns = ["standardId"])],
)
data class TuneStateEntity(
  @PrimaryKey val standardId: Long,
  val lifecycle: Lifecycle,
  val keyPolicy: KeyPolicy = KeyPolicy.FIXED,
  val cyclePosition: Int = 0,
  val notes: String? = null,
  val wishlistedAt: Long? = null,
  val introducedAt: Long? = null,
  val reps: Int = 0,
  val lapses: Int = 0,
  val intervalDays: Double = 0.0,
  val ease: Double = 2.5,
  val dueAt: Long? = null,
  val lastReviewedAt: Long? = null,
) {
  val cardState: CardState
    get() = CardState(reps, lapses, intervalDays, ease, dueAt)

  fun withCardState(s: CardState) = copy(reps = s.reps, lapses = s.lapses, intervalDays = s.intervalDays, ease = s.ease, dueAt = s.dueAt)
}

/** One graded rep. The scheduler's inputs and outputs are kept so history can be replayed under another scheduler. */
@Entity(tableName = "review_events", indices = [Index(value = ["itemKind", "itemId"])])
data class ReviewEventEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val itemKind: String,
  val itemId: Long,
  val exerciseId: Long?,
  val transposition: String,
  /** Null when the tune's key does not parse. */
  val keyWrittenPitchClass: Int?,
  val keyWrittenMinor: Boolean?,
  val grade: Grade,
  val reviewedAt: Long,
  val elapsedMs: Long,
  val schedulerId: String,
  val schedulerVersion: Int,
  val intervalBefore: Double,
  val intervalAfter: Double,
  val easeBefore: Double,
  val easeAfter: Double,
) {
  companion object {
    const val TUNE = "TUNE"
  }
}

/** User-owned notes on how well a tune is known; kept apart from scheduling so clearing one leaves the other. */
@Entity(
  tableName = "tune_knowledge",
  foreignKeys = [ForeignKey(entity = StandardEntity::class, parentColumns = ["id"], childColumns = ["standardId"])],
)
data class TuneKnowledgeEntity(
  @PrimaryKey val standardId: Long,
  val comfort: Comfort? = null,
  /** See [com.larakelley.woodshed.domain.KeyMask]. */
  val knownKeys: Int = 0,
  val updatedAt: Long = 0,
)
