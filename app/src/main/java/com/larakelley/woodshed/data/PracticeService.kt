package com.larakelley.woodshed.data

import androidx.room.withTransaction
import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.ReviewEventEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.domain.CardState
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.KeyPolicy
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.Scheduler
import com.larakelley.woodshed.domain.Sm2Scheduler
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.domain.concertKeyFor
import com.larakelley.woodshed.domain.pickExercise
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

data class PracticeSummary(val due: Int, val newToday: Int, val wishlist: Int, val reviewedToday: Int)

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

  /** Only a tune that has never been practiced can leave the lists entirely; anything else is retired. */
  suspend fun removeFromWishlist(standardId: Long) {
    if (states.get(standardId)?.lifecycle == Lifecycle.WISHLIST) states.delete(standardId)
  }

  /** Starts learning now, outside the daily limit. */
  suspend fun startLearning(standardId: Long) {
    val existing = states.get(standardId)
    val t = now()
    states.upsert(
      (existing ?: TuneStateEntity(standardId, Lifecycle.LEARNING)).copy(lifecycle = Lifecycle.LEARNING, introducedAt = existing?.introducedAt ?: t, dueAt = t)
    )
  }

  /** For tunes already known: straight into repertoire, due now so the first review calibrates it. */
  suspend fun markKnown(standardId: Long) {
    val existing = states.get(standardId)
    val t = now()
    val base = existing ?: TuneStateEntity(standardId, Lifecycle.REPERTOIRE)
    states.upsert(base.copy(lifecycle = Lifecycle.REPERTOIRE, introducedAt = base.introducedAt ?: t, reps = maxOf(base.reps, 2), intervalDays = maxOf(base.intervalDays, 7.0), dueAt = t))
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

  /** Moves the oldest wishlist tunes into learning until today's introductions reach [dailyLimit]. */
  suspend fun introduceNew(dailyLimit: Int): Int = db.withTransaction {
    val t = now()
    val room = dailyLimit - states.introducedSince(startOfDay(t))
    if (room <= 0) return@withTransaction 0
    val chosen = states.wishlist().take(room)
    chosen.forEach { states.upsert(it.copy(lifecycle = Lifecycle.LEARNING, introducedAt = t, dueAt = t)) }
    chosen.size
  }

  suspend fun summary(): PracticeSummary {
    val t = now()
    val day = startOfDay(t)
    return PracticeSummary(
      due = states.due(t).size,
      newToday = states.introducedSince(day),
      wishlist = states.wishlist().size,
      reviewedToday = db.reviewEvents().countSince(day),
    )
  }

  /** The most overdue tune as a card, or null when nothing is due. */
  suspend fun nextCard(transposition: Transposition): Card? {
    val t = now()
    val exercises = db.exercises().all().filter { it.appliesToTune && it.archivedAt == null }
    for (state in states.due(t)) {
      val tune = db.standards().byId(state.standardId) ?: continue
      if (tune.archivedAt != null) continue
      val recent = db.reviewEvents().forItem(ReviewEventEntity.TUNE, tune.id).mapNotNull { it.exerciseId }.reversed()
      val exercise = pickExercise(exercises, { it.id }, recent.distinct(), random)
      return Card(tune, state, exercise, concertKeyFor(tune.commonKeyRaw, state.keyPolicy, state.cyclePosition), transposition, t)
    }
    return null
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
      )
    )
    val lifecycle = when {
      grade == Grade.AGAIN -> Lifecycle.LEARNING
      after.intervalDays >= Sm2Scheduler.REPERTOIRE_INTERVAL_DAYS -> Lifecycle.REPERTOIRE
      else -> before.lifecycle
    }
    val cycle = if (before.keyPolicy == KeyPolicy.CYCLE) before.cyclePosition + 1 else before.cyclePosition
    states.upsert(before.withCardState(after).copy(lifecycle = lifecycle, cyclePosition = cycle, lastReviewedAt = t))
  }

  private fun startOfDay(t: Long): Long = Instant.ofEpochMilli(t).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
}
