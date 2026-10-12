package com.larakelley.woodshed.domain

import kotlin.random.Random

/** The key a tune is practiced in for one review, in concert pitch. Null when the tune's key does not parse. */
fun concertKeyFor(rawKey: String?, policy: KeyPolicy, cyclePosition: Int): Key? {
  val base = Key.parse(rawKey) ?: return null
  if (policy == KeyPolicy.FIXED) return base
  val start = Key.CIRCLE_OF_FOURTHS.indexOf(base.pitchClass)
  return base.copy(pitchClass = Key.CIRCLE_OF_FOURTHS[Math.floorMod(start + cyclePosition, 12)])
}

/**
 * Picks the exercise to pair with a tune: one never used on it if any, otherwise the one used longest ago.
 * [recentIds] is this tune's exercise history, most recent first.
 */
fun <T> pickExercise(candidates: List<T>, idOf: (T) -> Long, recentIds: List<Long>, random: Random): T {
  require(candidates.isNotEmpty()) { "no exercises apply to tunes" }
  val unused = candidates.filter { idOf(it) !in recentIds }
  if (unused.isNotEmpty()) return unused.random(random)
  return candidates.maxBy { recentIds.indexOf(idOf(it)) }
}
