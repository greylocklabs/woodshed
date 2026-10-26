package com.larakelley.woodshed.data

import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.TranscriptionEntity
import com.larakelley.woodshed.data.db.TranscriptionStateEntity
import com.larakelley.woodshed.data.db.TranscriptionStatus
import com.larakelley.woodshed.data.db.TuneKnowledgeEntity
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.data.db.homeKey
import com.larakelley.woodshed.domain.ChangesLibrary
import com.larakelley.woodshed.domain.ChangesSection
import com.larakelley.woodshed.domain.Instrument
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

/** What the dice roll: a tune to play over, an exercise, a solo to transcribe, or changes to play over. */
enum class RollKind(val label: String) {
  TUNE("🎵 Tune"),
  EXERCISE("💪 Exercise"),
  SOLO("🎧 Solo"),
  CHANGES("🎹 Changes"),
}

/** Which changes the dice draw from: a section of the Changes tab, the user's own, or any. */
enum class ChangesPool(val label: String, val section: ChangesSection? = null) {
  ANY("Any changes"),
  BLUES("Blues", ChangesSection.BLUES),
  FORMS("Forms", ChangesSection.FORMS),
  CADENCES("Cadences and turnarounds", ChangesSection.CADENCES),
  PATTERNS("Patterns", ChangesSection.PATTERNS),
  MINE("My changes"),
}

/** Changes to play: [route] opens them on the Changes tab, as "form:blues" does. */
data class ChangesPick(val route: String, val title: String, val description: String)

/** Draws changes from [pool], the built-in ones or [mine] (by id and name), or null when none qualifies. */
fun rollChanges(mine: List<Pair<Long, String>>, pool: ChangesPool, random: Random): ChangesPick? {
  val forms = ChangesLibrary.forms.filter {
    pool == ChangesPool.ANY || it.section == pool.section
  }.map { ChangesPick("form:${it.id}", it.name, it.description) }
  val patterns = ChangesLibrary.patterns.takeIf { pool == ChangesPool.ANY || pool == ChangesPool.PATTERNS }.orEmpty().map {
    ChangesPick("pattern:${it.id}", it.name, it.description)
  }
  val own = mine.takeIf {
    pool == ChangesPool.ANY || pool == ChangesPool.MINE
  }.orEmpty().map { (id, name) -> ChangesPick("mine:$id", name, "Changes of your own") }
  return (forms + patterns + own).randomOrNull(random)
}

/** Which solos the dice draw from. */
enum class SoloPool(val label: String) {
  MINE("My solos"),
  TRANSCRIBING("Transcribing"),
  WISHLIST("Wishlist"),
  ANY("Any solo"),
}

/** How the dice choose a key. */
enum class KeyChoice(val label: String) {
  USUAL("Usual key"),
  ANY("Any key"),
  NOT_YET_KNOWN("A key I haven't checked off"),
}

data class DiceConstraints(
  val kind: RollKind = RollKind.TUNE,
  val soloPool: SoloPool = SoloPool.MINE,
  val pool: TunePool = TunePool.MY_TUNES,
  val style: String? = null,
  val collection: String? = null,
  /** A specific tune exercise, or null for any of them. */
  val exerciseName: String? = null,
  val keyChoice: KeyChoice = KeyChoice.USUAL,
  /** Roll only from this playlist's tunes, whatever [pool] says. */
  val playlistId: Long? = null,
  /** For an exercise roll: a category, or null for any. */
  val exerciseCategory: String? = null,
  /** For an exercise roll: a difficulty from 1 to 5, or null for any. */
  val exerciseDifficulty: Int? = null,
  /** For a changes roll: where the changes come from. */
  val changesPool: ChangesPool = ChangesPool.ANY,
)

data class Roll(
  val tune: StandardEntity,
  val state: TuneStateEntity?,
  val exercise: ExerciseEntity,
  /** Concert pitch; null when the usual key is wanted and the tune's key does not parse. */
  val concertKey: Key?,
)

/** Draws a solo from [pool], or null when none qualifies. */
fun rollSolo(solos: List<TranscriptionEntity>, states: Map<Long, TranscriptionStateEntity>, pool: SoloPool, random: Random): TranscriptionEntity? =
  solos.filter { s ->
    val status = states[s.id]?.status
    s.archivedAt == null && when (pool) {
      SoloPool.MINE -> status != null
      SoloPool.TRANSCRIBING -> status == TranscriptionStatus.ACTIVE
      SoloPool.WISHLIST -> status == TranscriptionStatus.WISHLIST
      SoloPool.ANY -> true
    }
  }.randomOrNull(random)

/** Draws an exercise that suits [instrument] and [c]'s category and difficulty, or null when none qualifies. */
fun rollExercise(exercises: List<ExerciseEntity>, instrument: Instrument?, c: DiceConstraints, random: Random): ExerciseEntity? = exercises.filter { e ->
  e.archivedAt == null &&
    (instrument == null || instrument.suits(e.families, e.category)) &&
    (c.exerciseCategory == null || e.category == c.exerciseCategory) &&
    (c.exerciseDifficulty == null || e.difficulty == c.exerciseDifficulty)
}.randomOrNull(random)

/** Draws a tune, an exercise and a key that satisfy [c], or null when no tune qualifies. */
fun roll(
  tunes: List<StandardEntity>,
  states: Map<Long, TuneStateEntity>,
  knowledge: Map<Long, TuneKnowledgeEntity>,
  exercises: List<ExerciseEntity>,
  c: DiceConstraints,
  random: Random,
  /** The chosen playlist's tunes, when [DiceConstraints.playlistId] is set. */
  playlist: Set<Long>? = null,
): Roll? {
  val pool = tunes.filter { t ->
    val lifecycle = states[t.id]?.lifecycle
    val inPool = if (playlist != null) {
      t.id in playlist
    } else {
      when (c.pool) {
        TunePool.MY_TUNES -> lifecycle != null && lifecycle != Lifecycle.RETIRED
        TunePool.IN_PRACTICE -> lifecycle == Lifecycle.LEARNING || lifecycle == Lifecycle.REPERTOIRE
        TunePool.WISHLIST -> lifecycle == Lifecycle.WISHLIST
        TunePool.ANY -> true
      }
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
