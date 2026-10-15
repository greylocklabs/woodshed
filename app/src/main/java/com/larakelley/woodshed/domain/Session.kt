package com.larakelley.woodshed.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.random.Random

/** What a session concentrates on, as the exercise categories that train it. */
enum class Focus(val label: String, val emoji: String, val categories: List<String>) {
  TONE("Tone", "🌬️", listOf("Tone", "Air & Breathing")),
  ARTICULATION("Articulation", "👅", listOf("Articulation")),
  SPEED("Speed", "⚡", listOf("Technique", "Scales & Patterns", "Licks")),
  TIME("Time", "⏱️", listOf("Rhythm & Time")),
  HARMONY("Harmony", "🎹", listOf("ii-V-I & Progressions")),
  EAR("Ear", "👂", listOf("Ear Training", "Transcription")),
  IMPROV("Improv", "💡", listOf("Improvisation", "Etudes")),
  READING("Reading", "📖", listOf("Reading")),
}

/** The planner's view of an exercise, so planning stays free of the database. */
data class PlanExercise(val id: Long, val category: String, val minutes: Int, val appliesToTune: Boolean, val difficulty: Int = 1)

@Serializable
sealed interface PlanBlock {
  val minutes: Int

  @Serializable
  @SerialName("exercise")
  data class Exercise(
    val exerciseId: Long,
    override val minutes: Int,
    /** The concert pitch class of the drone, for an exercise played against one. */
    val dronePitchClass: Int? = null,
  ) : PlanBlock

  /** A tune worked through one tune exercise. */
  @Serializable
  @SerialName("tune")
  data class Tune(val tuneId: Long, val exerciseId: Long, override val minutes: Int) : PlanBlock

  /** Time on the solo being transcribed. */
  @Serializable
  @SerialName("solo")
  data class Solo(val transcriptionId: Long, override val minutes: Int) : PlanBlock
}

const val MIN_BLOCK_MINUTES = 3

/** Whether an exercise is played against a drone, which its instructions say. */
fun usesDrone(description: String): Boolean = description.contains("drone", ignoreCase = true)

/** Warm-ups stay easy; harder tone work belongs with the Tone focus. */
const val MAX_WARMUP_DIFFICULTY = 2

/** A quarter of the session for the solo being transcribed, from 5 to 15 minutes; none in sessions under 15. */
fun soloMinutes(total: Int): Int = if (total < 15) 0 else (total / 4).coerceIn(5, 15)

/** Puts the solo block just before the tunes, or at the end when there are none. */
fun withSolo(blocks: List<PlanBlock>, solo: PlanBlock.Solo): List<PlanBlock> {
  val at = blocks.indexOfFirst { it is PlanBlock.Tune }.let { if (it < 0) blocks.size else it }
  return blocks.take(at) + solo + blocks.drop(at)
}

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
  var warmedUp = false

  drills.filter { it.category in Focus.TONE.categories && it.difficulty <= MAX_WARMUP_DIFFICULTY }.randomOrNull(random)?.let { warmup ->
    val minutes = minOf(warmup.minutes, warmupMinutes(totalMinutes), left)
    if (minutes >= MIN_BLOCK_MINUTES) {
      blocks += PlanBlock.Exercise(warmup.id, minutes)
      warmedUp = true
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

  // Minutes no block took, such as when the exercises run out with no tunes picked, are shared out so the plan fills
  // the session.
  return fill(blocks, totalMinutes, keepFirst = warmedUp)
}

/**
 * Shares the minutes [blocks] fall short of [totalMinutes] out a minute at a time, so a plan fills its session. A
 * solo keeps its share, and so does a warm-up at the start when [keepFirst], unless nothing else can take the time.
 */
fun fill(blocks: List<PlanBlock>, totalMinutes: Int, keepFirst: Boolean): List<PlanBlock> {
  var left = totalMinutes - blocks.sumOf { it.minutes }
  val fixed = blocks.indices.filter { blocks[it] is PlanBlock.Solo || (keepFirst && it == 0) }.toSet()
  val stretchable = blocks.indices.filter { it !in fixed }.ifEmpty { blocks.indices.toList() }
  val out = blocks.toMutableList()
  var i = 0
  while (left > 0 && stretchable.isNotEmpty()) {
    val at = stretchable[i % stretchable.size]
    out[at] = when (val b = out[at]) {
      is PlanBlock.Exercise -> b.copy(minutes = b.minutes + 1)
      is PlanBlock.Tune -> b.copy(minutes = b.minutes + 1)
      is PlanBlock.Solo -> b.copy(minutes = b.minutes + 1)
    }
    left--
    i++
  }
  return out
}
