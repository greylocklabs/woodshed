package com.larakelley.woodshed.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larakelley.woodshed.data.Card
import com.larakelley.woodshed.data.PracticeService
import com.larakelley.woodshed.data.PracticeSummary
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.TranscriptionEntity
import com.larakelley.woodshed.data.db.TranscriptionStateEntity
import com.larakelley.woodshed.data.db.TranscriptionStatus
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.domain.ClickTrack
import com.larakelley.woodshed.domain.Focus
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.Instrument
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.MAX_WARMUP_DIFFICULTY
import com.larakelley.woodshed.domain.PlanBlock
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.domain.delayLabel
import com.larakelley.woodshed.domain.fill
import com.larakelley.woodshed.domain.titleSortKey
import com.larakelley.woodshed.domain.usesDrone
import com.larakelley.woodshed.session.SessionRunner
import com.larakelley.woodshed.ui.browse.ExerciseRow
import com.larakelley.woodshed.ui.browse.exerciseRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** What the review screen shows for one card. */
data class CardView(
  val title: String,
  val key: String,
  val exercise: String,
  val instructions: String,
  val shape: String,
  val buttons: List<Pair<Grade, String>>,
  val beatsPerBar: Int = 4,
  /** The key the card is in, in concert pitch, for a drone on it. */
  val concertPitchClass: Int? = null,
)

fun cardView(card: Card, preview: Map<Grade, Long>, now: Long): CardView {
  val key = when {
    card.writtenKey == null -> "Key unknown"
    card.transposition == Transposition.C -> card.writtenKey!!.displayName
    else -> "Written ${card.writtenKey!!.displayName}"
  }
  val shape = listOfNotNull(card.tune.form, card.tune.bars?.let { "$it bars" }, card.tune.style.takeIf { it != card.tune.form }).joinToString(", ")
  return CardView(
    title = card.tune.title,
    key = key,
    exercise = card.exercise.name,
    instructions = card.exercise.description,
    shape = shape,
    buttons = Grade.entries.map { it to delayLabel(now, preview.getValue(it)) },
    beatsPerBar = ClickTrack.beatsPerBar(card.tune.timeSignature),
    concertPitchClass = card.concertKey?.pitchClass,
  )
}

/** Where a tune sits in the setup list. */
enum class TuneGroup(val label: String, val emoji: String) {
  DUE("Due for review", "🔔"),
  LEARNING("Learning", "🌱"),
  REPERTOIRE("Repertoire", "⭐"),
  WISHLIST("Wishlist", "💭"),

  /** Tunes from the chosen playlist that are not in the user's practice yet. */
  PLAYLIST("From the playlist", "📋"),
}

data class SessionTune(val id: Long, val title: String, val group: TuneGroup)

/** Tunes being practiced or wished for, grouped for the setup screen. Due tunes come first, most overdue first. */
fun sessionTunes(standards: List<StandardEntity>, states: List<TuneStateEntity>, now: Long): List<SessionTune> {
  val byId = standards.associateBy { it.id }
  return states.mapNotNull { s ->
    val tune = byId[s.standardId]?.takeIf { it.archivedAt == null } ?: return@mapNotNull null
    val due = s.dueAt != null && s.dueAt <= now
    val group = when (s.lifecycle) {
      Lifecycle.LEARNING -> if (due) TuneGroup.DUE else TuneGroup.LEARNING
      Lifecycle.REPERTOIRE -> if (due) TuneGroup.DUE else TuneGroup.REPERTOIRE
      Lifecycle.WISHLIST -> TuneGroup.WISHLIST
      Lifecycle.RETIRED -> return@mapNotNull null
    }
    Triple(SessionTune(tune.id, tune.title, group), s.dueAt ?: 0L, titleSortKey(tune.title))
  }
    .sortedWith(compareBy({ it.first.group.ordinal }, { if (it.first.group == TuneGroup.DUE) it.second else 0L }, { it.third }))
    .map { it.first }
}

/** A solo the session can include: one being transcribed or on the wishlist. */
data class SessionSolo(val id: Long, val title: String, val soloist: String, val active: Boolean)

/** Solos on the user's list and not yet done, the ones being transcribed first, most due first. */
fun sessionSolos(solos: List<TranscriptionEntity>, states: List<TranscriptionStateEntity>): List<SessionSolo> {
  val byId = solos.filter { it.archivedAt == null }.associateBy { it.id }
  return states.filter { it.status != TranscriptionStatus.DONE && it.transcriptionId in byId }
    .sortedWith(compareBy({ it.status != TranscriptionStatus.ACTIVE }, { it.dueAt ?: Long.MAX_VALUE }, { byId.getValue(it.transcriptionId).title.lowercase() }))
    .map { s -> byId.getValue(s.transcriptionId).let { SessionSolo(it.id, it.title, it.soloist, s.status == TranscriptionStatus.ACTIVE) } }
}

/** One row of a plan, as the setup screen lists it. [exercise] is the block's exercise, for its details. */
data class PlanItem(val block: PlanBlock, val title: String, val detail: String, val exercise: ExerciseRow? = null)

data class SetupUi(
  /** Null until chosen; a session cannot be planned without it. */
  val instrument: Instrument? = null,
  /** The instruments to offer: the ones the user plays, or every one until they say. */
  val instruments: List<Instrument> = Instrument.entries,
  /** False until the user picks their instruments in Settings. */
  val instrumentsChosen: Boolean = false,
  val minutes: Int = 30,
  val focus: Set<Focus> = emptySet(),
  val tunes: List<SessionTune> = emptyList(),
  val selected: Set<Long> = emptySet(),
  /** Playlists to pick tunes from, by id and name. */
  val playlists: List<Pair<Long, String>> = emptyList(),
  /** The playlist whose tunes were picked, until the selection is changed by hand. */
  val playlistId: Long? = null,
  val solos: List<SessionSolo> = emptyList(),
  /** The solo the session includes, or null for none. */
  val soloId: Long? = null,
  /** Null until a plan is built; changing the setup clears it. */
  val plan: List<PlanItem>? = null,
)

/** The drone's note for an exercise block, named for the user's transposition. */
data class DroneNote(val concertPitchClass: Int, val label: String, val notes: String?)

/**
 * Names the drone's [concertPitchClass] as the user reads it. Long tones and other tone work also say which notes to
 * hold: the drone's own note, then its major scale.
 */
fun droneNote(concertPitchClass: Int, category: String, transposition: Transposition): DroneNote {
  val concert = Key(concertPitchClass, minor = false).displayName
  val written = Key(concertPitchClass, minor = false).writtenFor(transposition).displayName
  val label = if (transposition == Transposition.C) "Drone on $concert" else "Drone on written $written (concert $concert)"
  val notes = if (category == "Tone") "Start on $written, the drone's note, then hold each note of the $written major scale above it." else null
  return DroneNote(concertPitchClass, label, notes)
}

/** The block being worked through in a running session. */
sealed interface StepView {
  val minutes: Int

  data class Exercise(
    val name: String,
    val category: String,
    val description: String,
    val tempoBpm: Int?,
    override val minutes: Int,
    /** What to play against the drone, for an exercise that uses one. */
    val drone: DroneNote? = null,
  ) : StepView

  data class Tune(val card: CardView, override val minutes: Int) : StepView

  /** The solo being transcribed, with its padded player address and where the solo itself starts. */
  data class Solo(
    val title: String,
    val soloist: String,
    val notes: String?,
    val embedUrl: String?,
    val url: String,
    val soloStart: Int?,
    val buttons: List<Pair<Grade, String>>,
    override val minutes: Int,
  ) : StepView

  /** Changes to play over on the Changes tab, which [route] and [concertKey] open; [key] is written for the horn. */
  data class Changes(val title: String, val key: String, val route: String, val concertKey: Int, override val minutes: Int) : StepView
}

data class PracticeUi(
  val summary: PracticeSummary? = null,
  val setup: SetupUi = SetupUi(),
  val running: Boolean = false,
  val step: StepView? = null,
  val stepIndex: Int = 0,
  /** When the current block began, in epoch milliseconds. */
  val stepStartedAt: Long = 0,
  /** When the session was paused, or null while it runs. */
  val pausedAt: Long? = null,
  val stepCount: Int = 0,
  val reviewedThisSession: Int = 0,
)

const val SESSION_MINUTES_MIN = 5
const val SESSION_MINUTES_MAX = 180

class PracticeViewModel(
  private val repository: WoodshedRepository,
  private val practice: PracticeService,
  private val sessions: SessionRunner,
  private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
  private val _ui =
    MutableStateFlow(
      PracticeUi(setup = SetupUi(instrument = repository.instrument.value, minutes = repository.sessionMinutes, focus = repository.sessionFocus)),
    )
  val ui: StateFlow<PracticeUi> = _ui.asStateFlow()

  private var exercises: Map<Long, ExerciseEntity> = emptyMap()
  private var titles: Map<Long, String> = emptyMap()
  private var solos: Map<Long, TranscriptionEntity> = emptyMap()
  private var selectionTouched = false
  private var soloTouched = false

  init {
    refresh()
    viewModelScope.launch {
      sessions.state.collect { s ->
        val ended = _ui.value.running && !s.running
        _ui.update {
          it.copy(
            running = s.running,
            step = s.step,
            stepIndex = s.stepIndex,
            stepCount = s.stepCount,
            stepStartedAt = s.stepStartedAt,
            pausedAt = s.pausedAt,
            reviewedThisSession = s.reviewed,
            // However the session ended, here or from its notification, the next one starts from setup.
            setup = if (ended) it.setup.copy(plan = null) else it.setup,
          )
        }
        if (ended) refresh()
      }
    }
  }

  /** Reloads the tune list and summary; due tunes stay ticked until the user changes the selection. */
  fun refresh() {
    viewModelScope.launch {
      val standards = repository.standards.first()
      titles = standards.associate { it.id to it.title }
      exercises = repository.exercises.first().associateBy { it.id }
      val allSolos = repository.transcriptions.first()
      solos = allSolos.associateBy { it.id }
      val offeredSolos = sessionSolos(allSolos, repository.transcriptionStates.first())
      val lists = repository.playlists.first()
      val chosen = lists.firstOrNull { it.id == _ui.value.setup.playlistId }
      val own = sessionTunes(standards, repository.tuneStates.first(), now())
      // Playlist tunes not yet in practice join the list so they can be ticked; practising them starts them learning.
      val byId = standards.associateBy { it.id }
      val extra = chosen?.tuneIds.orEmpty().filter { id ->
        own.none { it.id == id }
      }.mapNotNull { byId[it] }.map { SessionTune(it.id, it.title, TuneGroup.PLAYLIST) }
      val tunes = own + extra
      val summary = practice.summary()
      _ui.update { ui ->
        val ids = tunes.map { it.id }.toSet()
        val selected = when {
          chosen != null && !selectionTouched -> chosen.tuneIds.toSet() intersect ids
          selectionTouched -> ui.setup.selected intersect ids
          else -> tunes.filter { it.group == TuneGroup.DUE }.map { it.id }.toSet()
        }
        val mine = repository.myInstruments.value
        val instrument = repository.instrument.value
        val offered = Instrument.entries.filter { mine.isEmpty() || it in mine || it == instrument }
        ui.copy(
          summary = summary,
          setup = ui.setup.copy(
            instrument = instrument,
            instruments = offered,
            instrumentsChosen = mine.isNotEmpty(),
            tunes = tunes,
            selected = selected,
            playlists = lists.map { it.id to it.name },
            playlistId = chosen?.id,
            solos = offeredSolos,
            // The solo being transcribed is included until the user picks another or none.
            soloId = if (soloTouched) ui.setup.soloId?.takeIf { id -> offeredSolos.any { it.id == id } } else offeredSolos.firstOrNull { it.active }?.id,
          ),
        )
      }
    }
  }

  /** Also sets the key the app writes in, and which exercises the plan may use. */
  fun setInstrument(instrument: Instrument) {
    repository.setInstrument(instrument)
    editSetup { it.copy(instrument = instrument) }
  }

  fun setMinutes(minutes: Int) = editSetup { it.copy(minutes = minutes.coerceIn(SESSION_MINUTES_MIN, SESSION_MINUTES_MAX)) }

  fun toggleFocus(focus: Focus) = editSetup { it.copy(focus = if (focus in it.focus) it.focus - focus else it.focus + focus) }

  /** Ticks exactly the playlist's tunes, or goes back to the due tunes when null. */
  fun usePlaylist(id: Long?) {
    selectionTouched = false
    _ui.update { it.copy(setup = it.setup.copy(playlistId = id, plan = null)) }
    refresh()
  }

  fun toggleTune(id: Long) {
    selectionTouched = true
    editSetup { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) }
  }

  fun pickSolo(id: Long?) {
    soloTouched = true
    editSetup { it.copy(soloId = id) }
  }

  private fun editSetup(change: (SetupUi) -> SetupUi) = _ui.update { it.copy(setup = change(it.setup).copy(plan = null)) }

  /** Back from the plan to the setup. */
  fun discardPlan() = editSetup { it }

  fun buildPlan() {
    val setup = _ui.value.setup
    val instrument = setup.instrument ?: return
    repository.rememberSession(setup.minutes, setup.focus)
    viewModelScope.launch {
      val tuneIds = setup.tunes.map { it.id }.filter { it in setup.selected }
      val blocks = practice.plan(setup.minutes, setup.focus, tuneIds, instrument, setup.soloId)
      _ui.update { it.copy(setup = it.setup.copy(plan = blocks.map(::planItem))) }
    }
  }

  /** Exercises that could stand in for a block's: the same kind, suited to the instrument, and not already planned. */
  fun replacements(index: Int): List<ExerciseRow> {
    val plan = _ui.value.setup.plan ?: return emptyList()
    val block = plan.getOrNull(index)?.block ?: return emptyList()
    val inPlan = plan.map { it.block }.filterIsInstance<PlanBlock.Exercise>().map { it.exerciseId }.toSet()
    val usable = exercises.values.filter { it.archivedAt == null && fitsFamily(it) }
    return when (block) {
      is PlanBlock.Exercise -> {
        val category = exercises[block.exerciseId]?.category
        usable.filter { it.category == category && !it.appliesToTune && it.id !in inPlan }
      }

      is PlanBlock.Tune -> usable.filter { it.appliesToTune && it.id != block.exerciseId }

      is PlanBlock.Solo, is PlanBlock.Changes -> emptyList()
    }.sortedBy { it.name.lowercase() }.map(::exerciseRow)
  }

  /** Replaces a block's exercise with a random one of [replacements], if there is one. */
  fun shuffle(index: Int) {
    replacements(index).randomOrNull()?.let { replace(index, it.id) }
  }

  /** Replaces a block's exercise with [exerciseId], keeping its length. */
  fun replace(index: Int, exerciseId: Long) {
    val plan = _ui.value.setup.plan ?: return
    val replaced = when (val block = plan.getOrNull(index)?.block) {
      is PlanBlock.Exercise -> {
        val droned = exercises[exerciseId]?.description?.let(::usesDrone) == true
        block.copy(exerciseId = exerciseId, dronePitchClass = if (droned) block.dronePitchClass ?: Random.nextInt(12) else null)
      }

      is PlanBlock.Tune -> block.copy(exerciseId = exerciseId)

      is PlanBlock.Solo, is PlanBlock.Changes, null -> return
    }
    _ui.update { it.copy(setup = it.setup.copy(plan = plan.toMutableList().also { p -> p[index] = planItem(replaced) })) }
  }

  /** Drops a block and shares its minutes among the rest, so the plan still fills the session's length. */
  fun removeBlock(index: Int) {
    val setup = _ui.value.setup
    val plan = setup.plan ?: return
    val kept = plan.filterIndexed { i, _ -> i != index }
    val first = (kept.firstOrNull()?.block as? PlanBlock.Exercise)?.let { exercises[it.exerciseId] }
    val warmup = first != null && first.category in Focus.TONE.categories && first.difficulty <= MAX_WARMUP_DIFFICULTY
    val filled = fill(kept.map { it.block }, setup.minutes, keepFirst = warmup)
    _ui.update { it.copy(setup = it.setup.copy(plan = filled.map(::planItem))) }
  }

  private fun fitsFamily(e: ExerciseEntity): Boolean = _ui.value.setup.instrument?.suits(e.families, e.category) ?: true

  private fun planItem(block: PlanBlock): PlanItem = when (block) {
    is PlanBlock.Exercise -> exercises[block.exerciseId].let { PlanItem(block, it?.name ?: "Exercise", it?.category ?: "", it?.let(::exerciseRow)) }

    is PlanBlock.Tune -> exercises[block.exerciseId].let { PlanItem(block, titles[block.tuneId] ?: "Tune", it?.name ?: "", it?.let(::exerciseRow)) }

    is PlanBlock.Solo -> solos[block.transcriptionId].let { PlanItem(block, "🎧 ${it?.title ?: "Solo"}", "Transcribe ${it?.soloist ?: ""}".trim()) }

    is PlanBlock.Changes -> {
      val written = Math.floorMod(block.concertKey + repository.transposition.value.offset, 12)
      PlanItem(block, "🎹 ${block.title}", "Changes in ${Key.MAJOR_SPELLING[written]}")
    }
  }

  fun start() {
    val plan = _ui.value.setup.plan?.takeIf { it.isNotEmpty() } ?: return
    sessions.start(plan.map { it.block })
  }

  /** Finishes an exercise block, or moves past a tune block without grading it. */
  fun next() = sessions.next()

  fun previous() = sessions.previous()

  fun grade(grade: Grade) = sessions.grade(grade)

  fun pause() = sessions.pause()

  fun resume() = sessions.resume()

  fun stop() = sessions.stop()
}
