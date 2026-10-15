package com.larakelley.woodshed.data

import androidx.room.withTransaction
import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.ReviewEventEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.TranscriptionEntity
import com.larakelley.woodshed.data.db.TranscriptionStatus
import com.larakelley.woodshed.data.db.TuneKnowledgeEntity
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.db.homeKey
import com.larakelley.woodshed.data.db.withOverride
import com.larakelley.woodshed.domain.CardState
import com.larakelley.woodshed.domain.Comfort
import com.larakelley.woodshed.domain.Focus
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.Instrument
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.KeyMask
import com.larakelley.woodshed.domain.KeyPolicy
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.PlanBlock
import com.larakelley.woodshed.domain.PlanExercise
import com.larakelley.woodshed.domain.Scheduler
import com.larakelley.woodshed.domain.Sm2Scheduler
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.domain.concertKeyFor
import com.larakelley.woodshed.domain.pickExercise
import com.larakelley.woodshed.domain.planSession
import com.larakelley.woodshed.domain.soloMinutes
import com.larakelley.woodshed.domain.withSolo
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** One prompt: a tune, an exercise to do to it, and the key to do it in. */
data class Card(
  val tune: StandardEntity,
  val state: TuneStateEntity,
  val exercise: ExerciseEntity,
  /** In concert pitch; null when the tune's key does not parse. */
  val concertKey: Key?,
  val transposition: Transposition,
  val shownAt: Long,
) {
  val writtenKey: Key?
    get() = concertKey?.writtenFor(transposition)
}

data class PracticeSummary(val due: Int, val wishlist: Int, val reviewedToday: Int)

class PracticeService(
  private val db: WoodshedDatabase,
  private val now: () -> Long = System::currentTimeMillis,
  private val zone: ZoneId = ZoneId.systemDefault(),
  private val scheduler: Scheduler = Sm2Scheduler,
  private val random: Random = Random.Default,
) {
  private val states get() = db.tuneStates()

  suspend fun addToWishlist(standardId: Long) {
    if (states.get(standardId) == null) states.upsert(TuneStateEntity(standardId, Lifecycle.WISHLIST, wishlistedAt = now()))
  }

  /** Back to the plain library: scheduling state goes, review history and comfort notes stay. */
  suspend fun removeFromMyTunes(standardId: Long) = states.delete(standardId)

  suspend fun setComfort(standardId: Long, comfort: Comfort?) = updateKnowledge(standardId) { it.copy(comfort = comfort) }

  /** Toggles a key, in concert pitch, as known for the tune. */
  suspend fun toggleKnownKey(standardId: Long, concertPitchClass: Int) =
    updateKnowledge(standardId) { it.copy(knownKeys = KeyMask.toggle(it.knownKeys, concertPitchClass)) }

  suspend fun knownKeys(standardId: Long): Int = db.tuneKnowledge().get(standardId)?.knownKeys ?: 0

  private suspend fun updateKnowledge(standardId: Long, change: (TuneKnowledgeEntity) -> TuneKnowledgeEntity) = db.withTransaction {
    val current = db.tuneKnowledge().get(standardId) ?: TuneKnowledgeEntity(standardId)
    db.tuneKnowledge().upsert(change(current).copy(updatedAt = now()))
  }

  /** Moves a tune into learning, due now. */
  suspend fun startLearning(standardId: Long) {
    val existing = states.get(standardId)
    val t = now()
    states.upsert(
      (existing ?: TuneStateEntity(standardId, Lifecycle.LEARNING)).copy(lifecycle = Lifecycle.LEARNING, introducedAt = existing?.introducedAt ?: t, dueAt = t),
    )
  }

  /** For tunes already known: straight into repertoire, due now so the first review calibrates it. */
  suspend fun markKnown(standardId: Long) {
    val existing = states.get(standardId)
    val t = now()
    val base = existing ?: TuneStateEntity(standardId, Lifecycle.REPERTOIRE)
    states.upsert(
      base.copy(
        lifecycle = Lifecycle.REPERTOIRE,
        introducedAt = base.introducedAt ?: t,
        reps = maxOf(base.reps, 2),
        intervalDays = maxOf(base.intervalDays, 7.0),
        dueAt = t,
      ),
    )
  }

  suspend fun retire(standardId: Long) {
    states.get(standardId)?.let { states.upsert(it.copy(lifecycle = Lifecycle.RETIRED)) }
  }

  /** Back into the queue from retirement, due now. */
  suspend fun restore(standardId: Long) {
    states.get(standardId)?.let {
      val lifecycle = if (it.intervalDays >= Sm2Scheduler.REPERTOIRE_INTERVAL_DAYS) Lifecycle.REPERTOIRE else Lifecycle.LEARNING
      states.upsert(it.copy(lifecycle = lifecycle, dueAt = now()))
    }
  }

  suspend fun setKeyPolicy(standardId: Long, policy: KeyPolicy) {
    states.get(standardId)?.let { states.upsert(it.copy(keyPolicy = policy)) }
  }

  suspend fun summary(): PracticeSummary {
    val t = now()
    val day = startOfDay(t)
    return PracticeSummary(
      due = states.due(t).size,
      wishlist = states.wishlist().size,
      reviewedToday = db.reviewEvents().countSince(day),
    )
  }

  /** A session plan including the solo being transcribed, if any; see the full form. */
  suspend fun plan(minutes: Int, focus: Set<Focus>, tuneIds: List<Long>, instrument: Instrument?): List<PlanBlock> =
    plan(minutes, focus, tuneIds, instrument, activeSoloId())

  /**
   * A session plan; see [planSession]. Exercises that don't suit [instrument] are left out; null keeps them all.
   * [soloId] takes its share of the time first; null leaves solos out.
   */
  suspend fun plan(minutes: Int, focus: Set<Focus>, tuneIds: List<Long>, instrument: Instrument?, soloId: Long?): List<PlanBlock> {
    val exercises = allExercises().filter { it.archivedAt == null && (instrument == null || instrument.suits(it.families, it.category)) }
    val tuneExercises = exercises.filter { it.appliesToTune }
    val history = tuneIds.associateWith { id -> db.reviewEvents().forItem(ReviewEventEntity.TUNE, id).mapNotNull { it.exerciseId }.reversed().distinct() }
    val solo = soloId?.let { db.transcriptions().byId(it) }?.takeIf { it.archivedAt == null }
    val forSolo = if (solo == null) 0 else soloMinutes(minutes)
    val blocks = planSession(
      totalMinutes = minutes - forSolo,
      focus = focus,
      tuneIds = tuneIds,
      exercises = exercises.map { PlanExercise(it.id, it.category, it.minutes, it.appliesToTune, it.difficulty) },
      tuneExercise = { id -> tuneExercises.takeIf { it.isNotEmpty() }?.let { pickExercise(it, { e -> e.id }, history.getValue(id), random).id } },
      random = random,
    )
    return if (solo != null && forSolo > 0) withSolo(blocks, PlanBlock.Solo(solo.id, forSolo)) else blocks
  }

  /** The solo being transcribed that is most due, if any. */
  suspend fun activeSoloId(): Long? {
    val live = db.transcriptions().all().filter { it.archivedAt == null }.map { it.id }.toSet()
    return db.transcriptions().states().filter { it.status == TranscriptionStatus.ACTIVE && it.transcriptionId in live }.minByOrNull {
      it.dueAt
        ?: Long.MAX_VALUE
    }?.transcriptionId
  }

  /** The card for a tune block, moving the tune into learning first if it is not already being practiced. */
  suspend fun cardForBlock(block: PlanBlock.Tune, transposition: Transposition): Card? {
    val tune = tune(block.tuneId) ?: return null
    val exercise = allExercises().firstOrNull { it.id == block.exerciseId } ?: return null
    val lifecycle = states.get(tune.id)?.lifecycle
    if (lifecycle != Lifecycle.LEARNING && lifecycle != Lifecycle.REPERTOIRE) startLearning(tune.id)
    val state = states.get(tune.id) ?: return null
    return Card(tune, state, exercise, concertKeyFor(tune.homeKey, state.keyPolicy, state.cyclePosition), transposition, now())
  }

  /** A random card within [constraints]; see [roll]. */
  suspend fun roll(constraints: DiceConstraints): Roll? = roll(
    tunes = allTunes(),
    states = states.all().associateBy { it.standardId },
    knowledge = db.tuneKnowledge().all().associateBy { it.standardId },
    exercises = allExercises(),
    c = constraints,
    random = random,
    playlist = constraints.playlistId?.let { id -> db.playlists().entries(id).map { it.standardId }.toSet() },
  )

  /** A random solo within [constraints]; see [rollSolo]. */
  suspend fun rollExercise(constraints: DiceConstraints, instrument: Instrument?): ExerciseEntity? =
    rollExercise(allExercises(), instrument, constraints, random)

  suspend fun rollSolo(constraints: DiceConstraints): TranscriptionEntity? =
    rollSolo(db.transcriptions().all(), db.transcriptions().states().associateBy { it.transcriptionId }, constraints.soloPool, random)

  /** A rolled tune that is in the practice queue can be graded like any other card. */
  fun cardFor(roll: Roll, transposition: Transposition, shownAt: Long = now()): Card? {
    val state = roll.state?.takeIf { it.lifecycle == Lifecycle.LEARNING || it.lifecycle == Lifecycle.REPERTOIRE } ?: return null
    return Card(roll.tune, state, roll.exercise, roll.concertKey, transposition, shownAt)
  }

  /** What each grade would schedule, for labelling the buttons. */
  fun preview(card: Card): Map<Grade, CardState> = Grade.entries.associateWith { scheduler.next(card.state.cardState, it, now()) }

  suspend fun grade(card: Card, grade: Grade) = db.withTransaction {
    val t = now()
    val before = states.get(card.tune.id) ?: card.state
    val after = scheduler.next(before.cardState, grade, t)
    val written = card.writtenKey
    db.reviewEvents().insert(
      ReviewEventEntity(
        itemKind = ReviewEventEntity.TUNE,
        itemId = card.tune.id,
        exerciseId = card.exercise.id,
        transposition = card.transposition.name,
        keyWrittenPitchClass = written?.pitchClass,
        keyWrittenMinor = written?.minor,
        grade = grade,
        reviewedAt = t,
        elapsedMs = t - card.shownAt,
        schedulerId = scheduler.id,
        schedulerVersion = scheduler.version,
        intervalBefore = before.intervalDays,
        intervalAfter = after.intervalDays,
        easeBefore = before.ease,
        easeAfter = after.ease,
      ),
    )
    val lifecycle = when {
      grade == Grade.AGAIN -> Lifecycle.LEARNING
      after.intervalDays >= Sm2Scheduler.REPERTOIRE_INTERVAL_DAYS -> Lifecycle.REPERTOIRE
      else -> before.lifecycle
    }
    val cycle = if (before.keyPolicy == KeyPolicy.CYCLE) before.cyclePosition + 1 else before.cyclePosition
    states.upsert(before.withCardState(after).copy(lifecycle = lifecycle, cyclePosition = cycle, lastReviewedAt = t))
  }

  /** Every exercise as the user sees it, with their edits applied. */
  private suspend fun allExercises(): List<ExerciseEntity> {
    val overrides = db.exerciseOverrides().all().associateBy { it.exerciseId }
    return db.exercises().all().map { it.withOverride(overrides[it.id]) }
  }

  private suspend fun allTunes(): List<StandardEntity> {
    val overrides = db.tuneOverrides().all().associateBy { it.standardId }
    return db.standards().all().map { it.withOverride(overrides[it.id]) }
  }

  /** A tune as the user sees it, with their edits applied. */
  private suspend fun tune(id: Long): StandardEntity? = db.standards().byId(id)?.withOverride(db.tuneOverrides().get(id))

  private fun startOfDay(t: Long): Long = Instant.ofEpochMilli(t).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
}
