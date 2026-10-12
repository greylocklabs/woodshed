package com.larakelley.woodshed.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larakelley.woodshed.data.Card
import com.larakelley.woodshed.data.PracticeService
import com.larakelley.woodshed.data.PracticeSummary
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.domain.ClickTrack
import com.larakelley.woodshed.domain.Focus
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.Instrument
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.PlanBlock
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.domain.delayLabel
import com.larakelley.woodshed.domain.titleSortKey
import com.larakelley.woodshed.ui.browse.ExerciseRow
import com.larakelley.woodshed.ui.browse.exerciseRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the review screen shows for one card. */
data class CardView(
  val title: String,
  val key: String,
  val exercise: String,
  val instructions: String,
  val shape: String,
  val buttons: List<Pair<Grade, String>>,
  val beatsPerBar: Int = 4,
)

fun cardView(card: Card, preview: Map<Grade, Long>, now: Long): CardView {
  val key = when {
    card.writtenKey == null -> "Key unknown"
    card.transposition == Transposition.C -> "Concert ${card.writtenKey!!.displayName}"
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
  )
}

/** Where a tune sits in the setup list. */
enum class TuneGroup(val label: String, val emoji: String) {
  DUE("Due for review", "🔔"),
  LEARNING("Learning", "🌱"),
  REPERTOIRE("Repertoire", "⭐"),
  WISHLIST("Wishlist", "💭"),
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
  /** Null until a plan is built; changing the setup clears it. */
  val plan: List<PlanItem>? = null,
)

/** The block being worked through in a running session. */
sealed interface StepView {
  val minutes: Int

  data class Exercise(val name: String, val category: String, val description: String, val tempoBpm: Int?, override val minutes: Int) : StepView

  data class Tune(val card: CardView, override val minutes: Int) : StepView
}

data class PracticeUi(
  val summary: PracticeSummary? = null,
  val setup: SetupUi = SetupUi(),
  val running: Boolean = false,
  val step: StepView? = null,
  val stepIndex: Int = 0,
  val stepCount: Int = 0,
  val reviewedThisSession: Int = 0,
)

const val SESSION_MINUTES_MIN = 5
const val SESSION_MINUTES_MAX = 180

class PracticeViewModel(
  private val repository: WoodshedRepository,
  private val practice: PracticeService,
  private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
  private val _ui = MutableStateFlow(PracticeUi(setup = SetupUi(instrument = repository.instrument.value, minutes = repository.sessionMinutes, focus = repository.sessionFocus)))
  val ui: StateFlow<PracticeUi> = _ui.asStateFlow()

  private var exercises: Map<Long, ExerciseEntity> = emptyMap()
  private var titles: Map<Long, String> = emptyMap()
  private var steps: List<PlanBlock> = emptyList()
  private var current: Card? = null
  private var selectionTouched = false

  init {
    refresh()
  }

  /** Reloads the tune list and summary; due tunes stay ticked until the user changes the selection. */
  fun refresh() {
    viewModelScope.launch {
      val standards = repository.standards.first()
      titles = standards.associate { it.id to it.title }
      exercises = repository.exercises.first().associateBy { it.id }
      val tunes = sessionTunes(standards, repository.tuneStates.first(), now())
      val summary = practice.summary()
      _ui.update { ui ->
        val ids = tunes.map { it.id }.toSet()
        val selected = if (selectionTouched) ui.setup.selected intersect ids else tunes.filter { it.group == TuneGroup.DUE }.map { it.id }.toSet()
        val mine = repository.myInstruments.value
        val instrument = repository.instrument.value
        val offered = Instrument.entries.filter { mine.isEmpty() || it in mine || it == instrument }
        ui.copy(
          summary = summary,
          setup = ui.setup.copy(instrument = instrument, instruments = offered, instrumentsChosen = mine.isNotEmpty(), tunes = tunes, selected = selected),
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

  fun toggleTune(id: Long) {
    selectionTouched = true
    editSetup { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) }
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
      val blocks = practice.plan(setup.minutes, setup.focus, tuneIds, instrument)
      _ui.update { it.copy(setup = it.setup.copy(plan = blocks.map(::planItem))) }
    }
  }

  /** Replaces a block's exercise with another of the same kind that is not already in the plan, if there is one. */
  fun swap(index: Int) {
    val plan = _ui.value.setup.plan ?: return
    val block = plan.getOrNull(index)?.block ?: return
    val inPlan = plan.map { it.block }.filterIsInstance<PlanBlock.Exercise>().map { it.exerciseId }.toSet()
    val usable = exercises.values.filter { it.archivedAt == null && fitsFamily(it) }
    val swapped = when (block) {
      is PlanBlock.Exercise -> {
        val category = exercises[block.exerciseId]?.category
        usable.filter { it.category == category && !it.appliesToTune && it.id !in inPlan }.randomOrNull()?.let { block.copy(exerciseId = it.id) }
      }
      is PlanBlock.Tune -> usable.filter { it.appliesToTune && it.id != block.exerciseId }.randomOrNull()?.let { block.copy(exerciseId = it.id) }
    } ?: return
    _ui.update { it.copy(setup = it.setup.copy(plan = plan.toMutableList().also { p -> p[index] = planItem(swapped) })) }
  }

  fun removeBlock(index: Int) {
    val plan = _ui.value.setup.plan ?: return
    _ui.update { it.copy(setup = it.setup.copy(plan = plan.filterIndexed { i, _ -> i != index })) }
  }

  private fun fitsFamily(e: ExerciseEntity): Boolean = _ui.value.setup.instrument?.suits(e.families, e.category) ?: true

  private fun planItem(block: PlanBlock): PlanItem = when (block) {
    is PlanBlock.Exercise -> exercises[block.exerciseId].let { PlanItem(block, it?.name ?: "Exercise", it?.category ?: "", it?.let(::exerciseRow)) }
    is PlanBlock.Tune -> exercises[block.exerciseId].let { PlanItem(block, titles[block.tuneId] ?: "Tune", it?.name ?: "", it?.let(::exerciseRow)) }
  }

  fun start() {
    val plan = _ui.value.setup.plan?.takeIf { it.isNotEmpty() } ?: return
    steps = plan.map { it.block }
    _ui.update { it.copy(running = true, stepIndex = 0, stepCount = steps.size, reviewedThisSession = 0) }
    viewModelScope.launch { show(0) }
  }

  /** Finishes an exercise block, or moves past a tune block without grading it. */
  fun next() {
    current = null
    viewModelScope.launch { show(_ui.value.stepIndex + 1) }
  }

  fun grade(grade: Grade) {
    val card = current ?: return
    current = null
    viewModelScope.launch {
      practice.grade(card, grade)
      _ui.update { it.copy(reviewedThisSession = it.reviewedThisSession + 1) }
      show(_ui.value.stepIndex + 1)
    }
  }

  fun stop() {
    current = null
    steps = emptyList()
    _ui.update { it.copy(running = false, step = null, setup = it.setup.copy(plan = null)) }
    refresh()
  }

  private suspend fun show(index: Int) {
    val block = steps.getOrNull(index)
    val step = when (block) {
      null -> null
      is PlanBlock.Exercise -> exercises[block.exerciseId]?.let { StepView.Exercise(it.name, it.category, it.description, it.tempoBpm, block.minutes) }
      is PlanBlock.Tune -> practice.cardForBlock(block, repository.transposition.value)?.let { card ->
        current = card
        val preview = practice.preview(card).mapValues { it.value.dueAt ?: now() }
        StepView.Tune(cardView(card, preview, now()), block.minutes)
      }
    }
    if (block != null && step == null) return show(index + 1)
    _ui.update { it.copy(step = step, stepIndex = index) }
  }
}
