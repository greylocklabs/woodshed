package com.larakelley.woodshed.data

import com.larakelley.woodshed.data.db.BlockOutcome
import com.larakelley.woodshed.data.db.ReviewEventEntity
import com.larakelley.woodshed.data.db.SessionBlockEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.Sm2Scheduler
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A block of a past session, named as it shows in the history. */
data class PastBlock(
  val title: String,
  val detail: String,
  val plannedMinutes: Int,
  /** Null for a block the session never reached. */
  val outcome: BlockOutcome?,
  val grade: Grade?,
  val elapsedMs: Long,
)

data class PastSession(val id: Long, val startedAt: Long, val endedAt: Long?, val plannedMinutes: Int, val completed: Boolean, val blocks: List<PastBlock>) {
  /** Time spent on the blocks, pauses left out. */
  val practicedMs: Long get() = blocks.sumOf { it.elapsedMs }
}

/** One day's practice: sessions and the time spent in them, and graded reviews from anywhere in the app. */
data class PracticeDay(val date: LocalDate, val practicedMs: Long, val reviews: Int, val sessions: Int = 0)

/** What the stats screen shows, from sessions and the review log. */
data class PracticeStats(
  val practicedMs: Long,
  val sessions: Int,
  /** Days in a row with practice, up to today, or up to yesterday while today has none yet. */
  val currentStreak: Int,
  val longestStreak: Int,
  /** The most graded tunes, by title and number of graded reps, most first. */
  val topTunes: List<Pair<String, Int>>,
  /** Graded reps per written key, by pitch class from C; reps whose key did not parse are left out. */
  val repsByKey: List<Int>,
  val learning: Int,
  val repertoire: Int,
  /** Tunes whose interval reached repertoire this calendar month. */
  val reachedRepertoireThisMonth: Int,
)

/** Reads past sessions and graded reviews back for the history and stats. */
class HistoryService(private val db: WoodshedDatabase) {
  /** Every session, newest first, with its blocks in order. */
  suspend fun sessions(): List<PastSession> {
    val exercises = db.exercises().all().associateBy { it.id }
    val tunes = db.standards().all().associate { it.id to it.title }
    val solos = db.transcriptions().all().associateBy { it.id }
    val blocks = db.sessions().allBlocks().groupBy { it.sessionId }
    return db.sessions().all().map { s ->
      PastSession(
        s.id,
        s.startedAt,
        s.endedAt,
        s.plannedMinutes,
        s.completed,
        blocks[s.id].orEmpty().map { b ->
          val (title, detail) = when (b.kind) {
            SessionBlockEntity.KIND_EXERCISE -> exercises[b.itemId].let { (it?.name ?: "Exercise") to (it?.category ?: "") }
            SessionBlockEntity.KIND_TUNE -> (tunes[b.itemId] ?: "Tune") to (b.exerciseId?.let { exercises[it]?.name } ?: "")
            else -> solos[b.itemId].let { (it?.title ?: "Solo") to "Transcribe ${it?.soloist ?: ""}".trim() }
          }
          PastBlock(title, detail, b.plannedMinutes, b.outcome, b.grade, b.elapsedMs)
        },
      )
    }
  }

  /** Practice by day in [zone], for days with a session or a graded review. */
  suspend fun days(sessions: List<PastSession>, zone: ZoneId): Map<LocalDate, PracticeDay> {
    fun dateOf(at: Long) = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val byDay = sessions.groupBy { dateOf(it.startedAt) }
    val reviews = db.reviewEvents().times().groupingBy(::dateOf).eachCount()
    return (byDay.keys + reviews.keys).associateWith { day ->
      val list = byDay[day].orEmpty()
      PracticeDay(day, list.sumOf { it.practicedMs }, reviews[day] ?: 0, list.size)
    }
  }

  /** Totals, streaks and spreads over everything practised, as of [today] in [zone]. */
  suspend fun stats(sessions: List<PastSession>, zone: ZoneId, today: LocalDate): PracticeStats {
    val events = db.reviewEvents().all()
    val days = days(sessions, zone).keys
    val tuneEvents = events.filter { it.itemKind == ReviewEventEntity.TUNE }
    val titles = db.standards().all().associate { it.id to it.title }
    val states = db.tuneStates().all()
    val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return PracticeStats(
      practicedMs = sessions.sumOf { it.practicedMs },
      sessions = sessions.size,
      currentStreak = streakEndingAt(days, if (today in days) today else today.minusDays(1)),
      longestStreak = days.maxOfOrNull { d -> if (d.minusDays(1) in days) 0 else streakStartingAt(days, d) } ?: 0,
      topTunes = tuneEvents.groupingBy { it.itemId }.eachCount().entries.sortedByDescending { it.value }.take(TOP_TUNES)
        .map { (id, n) -> (titles[id] ?: "Tune") to n },
      repsByKey = List(12) { pc -> events.count { it.keyWrittenPitchClass == pc } },
      learning = states.count { it.lifecycle == Lifecycle.LEARNING },
      repertoire = states.count { it.lifecycle == Lifecycle.REPERTOIRE },
      reachedRepertoireThisMonth = tuneEvents.filter {
        it.reviewedAt >= monthStart && it.intervalBefore < Sm2Scheduler.REPERTOIRE_INTERVAL_DAYS && it.intervalAfter >= Sm2Scheduler.REPERTOIRE_INTERVAL_DAYS
      }.map { it.itemId }.distinct().size,
    )
  }

  private fun streakEndingAt(days: Set<LocalDate>, last: LocalDate): Int = generateSequence(last) { it.minusDays(1) }.takeWhile { it in days }.count()

  private fun streakStartingAt(days: Set<LocalDate>, first: LocalDate): Int = generateSequence(first) { it.plusDays(1) }.takeWhile { it in days }.count()

  private companion object {
    const val TOP_TUNES = 5
  }
}
