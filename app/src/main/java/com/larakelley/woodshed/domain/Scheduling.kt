package com.larakelley.woodshed.domain

import kotlin.math.max

enum class Grade { AGAIN, HARD, GOOD, EASY }

/** Where a tune sits in the user's practice. No state at all means it is just in the library. */
enum class Lifecycle { WISHLIST, LEARNING, REPERTOIRE, RETIRED }

/** How a tune's key is chosen each time it comes up. */
enum class KeyPolicy {
  /** Always the tune's usual key. */
  FIXED,

  /** One step further round the circle of fourths each review, starting from the usual key. */
  CYCLE,
}

data class CardState(val reps: Int = 0, val lapses: Int = 0, val intervalDays: Double = 0.0, val ease: Double = 2.5, val dueAt: Long? = null)

/** Behind an interface so history can be replayed under a different algorithm later. */
interface Scheduler {
  val id: String
  val version: Int

  fun next(state: CardState, grade: Grade, at: Long): CardState
}

/** A plain SM-2 variant. Intervals are in days; "again" brings the card back within the session. */
object Sm2Scheduler : Scheduler {
  override val id = "sm2"
  override val version = 1

  const val MIN_EASE = 1.3
  const val AGAIN_DELAY_MS = 10 * 60 * 1000L
  private const val DAY_MS = 24 * 60 * 60 * 1000.0

  override fun next(state: CardState, grade: Grade, at: Long): CardState {
    if (grade == Grade.AGAIN) {
      return CardState(
        reps = 0,
        lapses = state.lapses + if (state.reps > 0) 1 else 0,
        intervalDays = 0.0,
        ease = max(MIN_EASE, state.ease - 0.2),
        dueAt = at + AGAIN_DELAY_MS,
      )
    }
    val ease = when (grade) {
      Grade.HARD -> max(MIN_EASE, state.ease - 0.15)
      Grade.EASY -> state.ease + 0.15
      else -> state.ease
    }
    val interval = when (state.reps) {
      0 -> if (grade == Grade.EASY) 4.0 else 1.0

      1 -> when (grade) {
        Grade.HARD -> 2.0
        Grade.EASY -> 6.0
        else -> 3.0
      }

      else -> when (grade) {
        Grade.HARD -> state.intervalDays * 1.2
        Grade.EASY -> state.intervalDays * ease * 1.3
        else -> state.intervalDays * ease
      }
    }.coerceAtLeast(1.0)
    return CardState(state.reps + 1, state.lapses, interval, ease, at + (interval * DAY_MS).toLong())
  }

  /** Interval at which a learning tune counts as repertoire. */
  const val REPERTOIRE_INTERVAL_DAYS = 21.0
}

/** Short human form of the delay until [dueAt], for grade buttons. */
fun delayLabel(from: Long, dueAt: Long): String {
  val minutes = (dueAt - from) / 60_000
  return when {
    minutes < 60 -> "${minutes}m"
    minutes < 24 * 60 -> "${minutes / 60}h"
    else -> "${Math.round(minutes / (24 * 60.0))}d"
  }
}

/** How comfortable the player is with a tune, set by hand and independent of scheduling. */
enum class Comfort(val label: String) {
  NEW("New to me"),
  SHAKY("Shaky"),
  GETTING_THERE("Getting there"),
  COMFORTABLE("Comfortable"),
  SOLID("Solid"),
}

/** Concert pitch classes stored as a 12-bit mask, so known keys survive a change of transposition. */
object KeyMask {
  fun contains(mask: Int, pitchClass: Int): Boolean = mask and (1 shl pitchClass) != 0

  fun toggle(mask: Int, pitchClass: Int): Int = mask xor (1 shl pitchClass)

  fun count(mask: Int): Int = Integer.bitCount(mask and 0xFFF)
}
