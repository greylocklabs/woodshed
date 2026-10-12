package com.larakelley.woodshed.data

import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.TuneKnowledgeEntity
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.data.db.homeKey
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.KeyMask
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.Tonality
import kotlin.random.Random

/** Which tunes the dice draw from. */
enum class TunePool(val label: String) {
  MY_TUNES("My tunes"),
  IN_PRACTICE("Learning and repertoire"),
  WISHLIST("Wishlist"),
  ANY("Any tune"),
}

/** How the dice choose a key. */
enum class KeyChoice(val label: String) {
  USUAL("Usual key"),
  ANY("Any key"),
  NOT_YET_KNOWN("A key I haven't checked off"),
}

data class DiceConstraints(
  val pool: TunePool = TunePool.MY_TUNES,
  val style: String? = null,
  val collection: String? = null,
  /** A specific tune exercise, or null for any of them. */
  val exerciseName: String? = null,
  val keyChoice: KeyChoice = KeyChoice.USUAL,
)

data class Roll(
  val tune: StandardEntity,
  val state: TuneStateEntity?,
  val exercise: ExerciseEntity,
  /** Concert pitch; null when the usual key is wanted and the tune's key does not parse. */
  val concertKey: Key?,
)

/** Draws a tune, an exercise and a key that satisfy [c], or null when no tune qualifies. */
fun roll(
  tunes: List<StandardEntity>,
  states: Map<Long, TuneStateEntity>,
  knowledge: Map<Long, TuneKnowledgeEntity>,
  exercises: List<ExerciseEntity>,
  c: DiceConstraints,
  random: Random,
): Roll? {
  val pool = tunes.filter { t ->
    val lifecycle = states[t.id]?.lifecycle
    val inPool = when (c.pool) {
      TunePool.MY_TUNES -> lifecycle != null && lifecycle != Lifecycle.RETIRED
      TunePool.IN_PRACTICE -> lifecycle == Lifecycle.LEARNING || lifecycle == Lifecycle.REPERTOIRE
      TunePool.WISHLIST -> lifecycle == Lifecycle.WISHLIST
      TunePool.ANY -> true
    }
    inPool && t.archivedAt == null && (c.style == null || t.style == c.style) && (c.collection == null || t.collection == c.collection)
  }
  val choices = exercises.filter { it.appliesToTune && it.archivedAt == null && (c.exerciseName == null || it.name == c.exerciseName) }
  if (pool.isEmpty() || choices.isEmpty()) return null

  val tune = pool.random(random)
  val usual = tune.homeKey
  val minor = tune.tonality == Tonality.MINOR
  val key = when (c.keyChoice) {
    KeyChoice.USUAL -> usual
    KeyChoice.ANY -> Key(random.nextInt(12), minor)
    KeyChoice.NOT_YET_KNOWN -> {
      val known = knowledge[tune.id]?.knownKeys ?: 0
      val unknown = (0..11).filterNot { KeyMask.contains(known, it) }
      Key((unknown.ifEmpty { (0..11).toList() }).random(random), minor)
    }
  }
  return Roll(tune, states[tune.id], choices.random(random), key)
}
