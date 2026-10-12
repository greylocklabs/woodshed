package com.larakelley.woodshed.domain

import kotlin.math.roundToInt
import kotlin.random.Random

/** What a session concentrates on, as the exercise categories that train it. */
enum class Focus(val label: String, val categories: List<String>) {
  TONE("Tone", listOf("Tone", "Air & Breathing")),
  ARTICULATION("Articulation", listOf("Articulation")),
  SPEED("Speed", listOf("Technique", "Scales & Patterns", "Licks")),
  TIME("Time", listOf("Rhythm & Time")),
  HARMONY("Harmony", listOf("ii-V-I & Progressions")),
  EAR("Ear", listOf("Ear Training", "Transcription")),
  IMPROV("Improv", listOf("Improvisation", "Etudes")),
  READING("Reading", listOf("Reading")),
}

/** The planner's view of an exercise, so planning stays free of the database. */
data class PlanExercise(val id: Long, val category: String, val minutes: Int, val appliesToTune: Boolean, val difficulty: Int = 1)

sealed interface PlanBlock {
  val minutes: Int

  data class Exercise(val exerciseId: Long, override val minutes: Int) : PlanBlock

  /** A tune worked through one tune exercise. */
  data class Tune(val tuneId: Long, val exerciseId: Long, override val minutes: Int) : PlanBlock
}

const val MIN_BLOCK_MINUTES = 3

/** Warm-ups stay easy; harder tone work belongs with the Tone focus. */
const val MAX_WARMUP_DIFFICULTY = 2

/** Five minutes of warm-up per half hour, at least three. */
private fun warmupMinutes(total: Int) = (total / 6).coerceIn(MIN_BLOCK_MINUTES, 10)

/**
 * Lays out a session of about [totalMinutes]: an easy tone warm-up, then exercises for [focus], then the chosen tunes.
 * Tunes get at least half of the time after the warm-up and at least five minutes each; tunes that do not fit are
 * left out from the end of [tuneIds]. With no focus, exercises come from every non-tune category. [tuneExercise]
 * picks the exercise to pair with a tune.
 */
fun planSession(
  totalMinutes: Int,
  focus: Set<Focus>,
  tuneIds: List<Long>,
  exercises: List<PlanExercise>,
  tuneExercise: (tuneId: Long) -> Long?,
  random: Random,
): List<PlanBlock> {
  val drills = exercises.filterNot { it.appliesToTune }
  val used = mutableSetOf<Long>()
  val blocks = mutableListOf<PlanBlock>()
  var left = totalMinutes

  drills.filter { it.category in Focus.TONE.categories && it.difficulty <= MAX_WARMUP_DIFFICULTY }.randomOrNull(random)?.let { warmup ->
    val minutes = minOf(warmup.minutes, warmupMinutes(totalMinutes), left)
    if (minutes >= MIN_BLOCK_MINUTES) {
      blocks += PlanBlock.Exercise(warmup.id, minutes)
      used += warmup.id
      left -= minutes
    }
  }

  // Exercises get about half of what is left when there are tunes; tunes then take everything remaining.
  var drillBudget = if (tuneIds.isEmpty()) left else left - (left * 0.5).roundToInt()
  val pools = (if (focus.isEmpty()) listOf(Phase.CATEGORY_ORDER) else Focus.entries.filter { it in focus }.map { it.categories })
    .map { cats -> drills.filter { it.category in cats && it.id !in used }.shuffled(random) }
    .filter { it.isNotEmpty() }
  // Round-robin across the focus areas so each gets a turn before any gets a second.
  var round = 0
  while (drillBudget >= MIN_BLOCK_MINUTES) {
    val candidates = pools.mapNotNull { it.getOrNull(round) }
    if (candidates.isEmpty()) break
    for (next in candidates) {
      val minutes = minOf(next.minutes, drillBudget)
      if (minutes < MIN_BLOCK_MINUTES || next.id in used) continue
      blocks += PlanBlock.Exercise(next.id, minutes)
      used += next.id
      drillBudget -= minutes
      left -= minutes
    }
    round++
  }

  val tuneCount = minOf(tuneIds.size, left / 5)
  tuneIds.take(tuneCount).forEachIndexed { i, id ->
    val minutes = left / tuneCount + if (i < left % tuneCount) 1 else 0
    tuneExercise(id)?.let { blocks += PlanBlock.Tune(id, it, minutes) }
  }
  if (tuneCount > 0) left = 0

  // Leftover minutes too short for a block of their own go to the last block.
  if (left in 1 until MIN_BLOCK_MINUTES && blocks.isNotEmpty()) {
    blocks[blocks.lastIndex] = when (val last = blocks.last()) {
      is PlanBlock.Exercise -> last.copy(minutes = last.minutes + left)
      is PlanBlock.Tune -> last.copy(minutes = last.minutes + left)
    }
  }
  return blocks
}
