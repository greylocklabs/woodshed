package com.larakelley.woodshed.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larakelley.woodshed.data.Card
import com.larakelley.woodshed.data.PracticeService
import com.larakelley.woodshed.data.PracticeSummary
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.domain.ClickTrack
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.domain.delayLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class PracticeUi(
  val summary: PracticeSummary? = null,
  val dailyNewLimit: Int = 1,
  val card: CardView? = null,
  val reviewing: Boolean = false,
  val reviewedThisSession: Int = 0,
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

class PracticeViewModel(
  private val repository: WoodshedRepository,
  private val practice: PracticeService,
  private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
  private val _ui = MutableStateFlow(PracticeUi(dailyNewLimit = repository.dailyNewLimit.value))
  val ui: StateFlow<PracticeUi> = _ui.asStateFlow()

  private var current: Card? = null

  init {
    refresh()
  }

  fun refresh() {
    viewModelScope.launch { _ui.update { it.copy(summary = practice.summary(), dailyNewLimit = repository.dailyNewLimit.value) } }
  }

  fun setDailyNewLimit(limit: Int) {
    repository.setDailyNewLimit(limit)
    refresh()
  }

  fun start() {
    viewModelScope.launch {
      practice.introduceNew(repository.dailyNewLimit.value)
      _ui.update { it.copy(reviewing = true, reviewedThisSession = 0) }
      showNext()
    }
  }

  fun grade(grade: Grade) {
    val card = current ?: return
    current = null
    viewModelScope.launch {
      practice.grade(card, grade)
      _ui.update { it.copy(reviewedThisSession = it.reviewedThisSession + 1) }
      showNext()
    }
  }

  fun stop() {
    current = null
    _ui.update { it.copy(reviewing = false, card = null) }
    refresh()
  }

  private suspend fun showNext() {
    val card = practice.nextCard(repository.transposition.value)
    current = card
    if (card == null) {
      _ui.update { it.copy(card = null, summary = practice.summary()) }
    } else {
      val preview = practice.preview(card).mapValues { it.value.dueAt ?: now() }
      _ui.update { it.copy(card = cardView(card, preview, now())) }
    }
  }
}
