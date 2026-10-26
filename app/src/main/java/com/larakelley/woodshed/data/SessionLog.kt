package com.larakelley.woodshed.data

import com.larakelley.woodshed.data.db.BlockOutcome
import com.larakelley.woodshed.data.db.SessionBlockEntity
import com.larakelley.woodshed.data.db.SessionEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.PlanBlock

/** Writes each session and how its blocks went, for the history and stats. */
class SessionLog(private val db: WoodshedDatabase) {
  /** Records a session starting with [plan]; returns its id. */
  suspend fun start(plan: List<PlanBlock>, instrument: String?, at: Long): Long {
    val id = db.sessions().insert(SessionEntity(startedAt = at, plannedMinutes = plan.sumOf { it.minutes }, instrument = instrument))
    db.sessions().insertBlocks(
      plan.mapIndexed { i, b ->
        when (b) {
          is PlanBlock.Exercise -> SessionBlockEntity(id, i, SessionBlockEntity.KIND_EXERCISE, b.exerciseId, plannedMinutes = b.minutes)
          is PlanBlock.Tune -> SessionBlockEntity(id, i, SessionBlockEntity.KIND_TUNE, b.tuneId, b.exerciseId, b.minutes)
          is PlanBlock.Solo -> SessionBlockEntity(id, i, SessionBlockEntity.KIND_SOLO, b.transcriptionId, plannedMinutes = b.minutes)
          is PlanBlock.Changes -> SessionBlockEntity(id, i, SessionBlockEntity.KIND_CHANGES, 0, plannedMinutes = b.minutes, itemName = b.title)
        }
      },
    )
    return id
  }

  /** Records how block [position] ended. Finishing it again, after going back to it, replaces the first record. */
  suspend fun finish(sessionId: Long, position: Int, outcome: BlockOutcome, grade: Grade?, elapsedMs: Long, at: Long) =
    db.sessions().finishBlock(sessionId, position, outcome, grade, elapsedMs, at)

  suspend fun end(sessionId: Long, at: Long, completed: Boolean) = db.sessions().end(sessionId, at, completed)
}
