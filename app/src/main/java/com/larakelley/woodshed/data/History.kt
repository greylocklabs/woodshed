package com.larakelley.woodshed.data

import com.larakelley.woodshed.data.db.BlockOutcome
import com.larakelley.woodshed.data.db.SessionBlockEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.domain.Grade
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

/** One day's practice: time in sessions and graded reviews from anywhere in the app. */
data class PracticeDay(val date: LocalDate, val practicedMs: Long, val reviews: Int)

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
    val practiced = sessions.groupBy { dateOf(it.startedAt) }.mapValues { (_, list) -> list.sumOf { it.practicedMs } }
    val reviews = db.reviewEvents().times().groupingBy(::dateOf).eachCount()
    return (practiced.keys + reviews.keys).associateWith { PracticeDay(it, practiced[it] ?: 0, reviews[it] ?: 0) }
  }
}
