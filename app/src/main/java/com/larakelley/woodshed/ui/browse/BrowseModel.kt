package com.larakelley.woodshed.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larakelley.woodshed.data.PracticeService
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.KeyPolicy
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.Phase
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.domain.titleSortKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TuneRow(
  val id: Long,
  val slug: String,
  val title: String,
  val credit: String,
  val shape: String,
  val key: String,
  val rawKey: String?,
  val state: TuneStateEntity? = null,
) {
  val status: String?
    get() = state?.lifecycle?.label
}

val Lifecycle.label: String
  get() = name.lowercase().replaceFirstChar { it.uppercase() }

data class ExerciseRow(val name: String, val summary: String, val description: String)

data class BrowseState(
  val transposition: Transposition = Transposition.C,
  val family: Family? = null,
  val tunes: List<TuneRow> = emptyList(),
  val exercises: List<ExerciseRow> = emptyList(),
  val seedError: String? = null,
)

/** The key to show for a tune: written for the transposition, or the raw text when it does not parse. */
fun keyLabel(raw: String?, transposition: Transposition): String {
  val key = Key.parse(raw) ?: return if (raw == null) "Key unknown" else "Concert $raw"
  return if (transposition == Transposition.C) "Concert ${key.displayName}" else key.writtenFor(transposition).displayName
}

fun tuneRows(standards: List<StandardEntity>, transposition: Transposition, states: Map<Long, TuneStateEntity> = emptyMap()): List<TuneRow> =
  standards.sortedBy { titleSortKey(it.title) }.map { s ->
    TuneRow(
      id = s.id,
      slug = s.slug,
      title = s.title,
      credit = listOfNotNull(s.composer, s.year?.toString()).joinToString(", "),
      shape = listOfNotNull(
        s.form,
        s.bars?.let { "$it bars" },
        s.timeSignature.takeIf { it != "4/4" },
        s.style.takeIf { it != s.form },
      ).joinToString(", "),
      key = keyLabel(s.commonKeyRaw, transposition),
      rawKey = s.commonKeyRaw,
      state = states[s.id],
    )
  }

fun exerciseRows(exercises: List<ExerciseEntity>, family: Family?): List<ExerciseRow> =
  exercises
    .filter { family == null || it.families.isEmpty() || family in it.families }
    .sortedWith(compareBy({ Phase.categoryRank(it.category) }, { it.id }))
    .map { e ->
      val tempo = e.tempoBpm?.let { ", $it bpm" } ?: ""
      ExerciseRow(e.name, "${e.category}, ${e.minutes} min, difficulty ${e.difficulty}$tempo", e.description)
    }

/** What the tune detail screen can do to a tune's practice state. */
data class TuneActions(
  val addToWishlist: (Long) -> Unit = {},
  val removeFromWishlist: (Long) -> Unit = {},
  val startLearning: (Long) -> Unit = {},
  val markKnown: (Long) -> Unit = {},
  val retire: (Long) -> Unit = {},
  val restore: (Long) -> Unit = {},
  val setKeyPolicy: (Long, KeyPolicy) -> Unit = { _, _ -> },
)

class BrowseViewModel(private val repository: WoodshedRepository, private val practice: PracticeService) : ViewModel() {
  private val library = combine(repository.standards, repository.tuneStates, repository.transposition) { standards, states, transposition ->
    tuneRows(standards, transposition, states.associateBy { it.standardId })
  }

  val state: StateFlow<BrowseState> =
    combine(library, repository.exercises, repository.transposition, repository.family, repository.seedError) {
        tunes, exercises, transposition, family, error ->
        BrowseState(transposition, family, tunes, exerciseRows(exercises, family), error)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BrowseState())

  val actions = TuneActions(
    addToWishlist = { launch { practice.addToWishlist(it) } },
    removeFromWishlist = { launch { practice.removeFromWishlist(it) } },
    startLearning = { launch { practice.startLearning(it) } },
    markKnown = { launch { practice.markKnown(it) } },
    retire = { launch { practice.retire(it) } },
    restore = { launch { practice.restore(it) } },
    setKeyPolicy = { id, policy -> launch { practice.setKeyPolicy(id, policy) } },
  )

  private fun launch(block: suspend () -> Unit) {
    viewModelScope.launch { block() }
  }

  fun selectTransposition(transposition: Transposition) = repository.setTransposition(transposition)

  fun selectFamily(family: Family?) = repository.setFamily(family)
}
