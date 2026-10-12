package com.larakelley.woodshed.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larakelley.woodshed.data.PracticeService
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.TuneKnowledgeEntity
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.data.db.homeKey
import com.larakelley.woodshed.domain.Comfort
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.KeyPolicy
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.Phase
import com.larakelley.woodshed.domain.Tonality
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.domain.titleSortKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TuneRow(
  val id: Long,
  val slug: String,
  val title: String,
  val composer: String?,
  val credit: String,
  val shape: String,
  val style: String?,
  val key: String,
  val homeKey: Key?,
  val tonality: Tonality?,
  val state: TuneStateEntity? = null,
  val knowledge: TuneKnowledgeEntity? = null,
  val transposition: Transposition = Transposition.C,
  val collection: String = "Standards",
  val verified: Boolean = true,
) {
  val status: String?
    get() = state?.lifecycle?.label
}

val Lifecycle.label: String
  get() = name.lowercase().replaceFirstChar { it.uppercase() }

data class ExerciseRow(val name: String, val category: String, val summary: String, val description: String)

/** Which tunes to list, by where they sit in the user's practice. */
enum class StatusFilter(val label: String) {
  ALL("All tunes"),
  MINE("My tunes"),
  WISHLIST("Wishlist"),
  LEARNING("Learning"),
  REPERTOIRE("Repertoire"),
  RETIRED("Retired"),
  LIBRARY("Not started"),
}

data class TuneFilter(
  val query: String = "",
  val status: StatusFilter = StatusFilter.ALL,
  val style: String? = null,
  /** Only tunes rated at least this comfortable. */
  val minComfort: Comfort? = null,
  val collection: String? = null,
  val tonality: Tonality? = null,
)

data class ExerciseFilter(val query: String = "", val category: String? = null)

data class BrowseState(
  val transposition: Transposition = Transposition.C,
  val family: Family? = null,
  val tunes: List<TuneRow> = emptyList(),
  val totalTunes: Int = 0,
  val exercises: List<ExerciseRow> = emptyList(),
  val totalExercises: Int = 0,
  val tuneFilter: TuneFilter = TuneFilter(),
  val exerciseFilter: ExerciseFilter = ExerciseFilter(),
  val styles: List<String> = emptyList(),
  val categories: List<String> = emptyList(),
  val seedError: String? = null,
)

/** The key to show for a tune, written for the transposition. */
fun keyLabel(key: Key?, tonality: Tonality?, transposition: Transposition): String {
  if (key == null) return tonality?.let { "${it.label}, key unknown" } ?: "Key unknown"
  return if (transposition == Transposition.C) "Concert ${key.displayName}" else key.writtenFor(transposition).displayName
}

fun tuneRows(
  standards: List<StandardEntity>,
  transposition: Transposition,
  states: Map<Long, TuneStateEntity> = emptyMap(),
  knowledge: Map<Long, TuneKnowledgeEntity> = emptyMap(),
): List<TuneRow> =
  standards.sortedBy { titleSortKey(it.title) }.map { s ->
    TuneRow(
      id = s.id,
      slug = s.slug,
      title = s.title,
      composer = s.composer,
      credit = listOfNotNull(s.composer, s.year?.toString()).joinToString(", "),
      shape = listOfNotNull(
        s.form,
        s.bars?.let { "$it bars" },
        s.timeSignature?.takeIf { it != "4/4" },
        s.style.takeIf { it != s.form },
      ).joinToString(", "),
      style = s.style,
      key = keyLabel(s.homeKey, s.tonality, transposition),
      homeKey = s.homeKey,
      tonality = s.tonality,
      state = states[s.id],
      knowledge = knowledge[s.id],
      transposition = transposition,
      collection = s.collection,
      verified = s.verified,
    )
  }

/** Lowercase letters and digits only, so "round midnight" finds "'Round Midnight". */
private fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() || it == ' ' }

/** Every word of the query must appear somewhere in [fields]. */
fun matchesQuery(query: String, vararg fields: String?): Boolean {
  val haystack = normalize(fields.filterNotNull().joinToString(" "))
  return normalize(query).split(' ').filter { it.isNotEmpty() }.all { it in haystack }
}

fun filterTunes(rows: List<TuneRow>, filter: TuneFilter): List<TuneRow> =
  rows.filter { row ->
    val lifecycle = row.state?.lifecycle
    val statusOk = when (filter.status) {
      StatusFilter.ALL -> true
      StatusFilter.MINE -> lifecycle != null
      StatusFilter.WISHLIST -> lifecycle == Lifecycle.WISHLIST
      StatusFilter.LEARNING -> lifecycle == Lifecycle.LEARNING
      StatusFilter.REPERTOIRE -> lifecycle == Lifecycle.REPERTOIRE
      StatusFilter.RETIRED -> lifecycle == Lifecycle.RETIRED
      StatusFilter.LIBRARY -> lifecycle == null
    }
    val comfortOk = filter.minComfort == null || (row.knowledge?.comfort?.let { it >= filter.minComfort } ?: false)
    statusOk && comfortOk &&
      (filter.style == null || row.style == filter.style) &&
      (filter.collection == null || row.collection == filter.collection) &&
      (filter.tonality == null || row.tonality == filter.tonality) &&
      matchesQuery(filter.query, row.title, row.composer)
  }

fun exerciseRows(exercises: List<ExerciseEntity>, family: Family?, filter: ExerciseFilter = ExerciseFilter()): List<ExerciseRow> =
  exercises
    .filter { family == null || it.families.isEmpty() || family in it.families }
    .filter { filter.category == null || it.category == filter.category }
    .filter { matchesQuery(filter.query, it.name, it.description) }
    .sortedWith(compareBy({ Phase.categoryRank(it.category) }, { it.id }))
    .map { e ->
      val tempo = e.tempoBpm?.let { ", $it bpm" } ?: ""
      ExerciseRow(e.name, e.category, "${e.category}, ${e.minutes} min, difficulty ${e.difficulty}$tempo", e.description)
    }

/** What the tune detail screen can do to a tune. */
data class TuneActions(
  val addToWishlist: (Long) -> Unit = {},
  val removeFromMyTunes: (Long) -> Unit = {},
  val startLearning: (Long) -> Unit = {},
  val markKnown: (Long) -> Unit = {},
  val retire: (Long) -> Unit = {},
  val restore: (Long) -> Unit = {},
  val setKeyPolicy: (Long, KeyPolicy) -> Unit = { _, _ -> },
  val setComfort: (Long, Comfort?) -> Unit = { _, _ -> },
  val toggleKnownKey: (Long, Int) -> Unit = { _, _ -> },
)

class BrowseViewModel(private val repository: WoodshedRepository, private val practice: PracticeService) : ViewModel() {
  private val tuneFilter = MutableStateFlow(TuneFilter())
  private val exerciseFilter = MutableStateFlow(ExerciseFilter())

  private val library = combine(repository.standards, repository.tuneStates, repository.tuneKnowledge, repository.transposition) {
      standards, states, knowledge, transposition ->
    tuneRows(standards, transposition, states.associateBy { it.standardId }, knowledge.associateBy { it.standardId })
  }

  private data class TunePart(val all: List<TuneRow>, val filter: TuneFilter, val rows: List<TuneRow>)

  private data class ExercisePart(val all: List<ExerciseEntity>, val family: Family?, val filter: ExerciseFilter, val rows: List<ExerciseRow>)

  private val tunePart = combine(library, tuneFilter) { rows, filter -> TunePart(rows, filter, filterTunes(rows, filter)) }

  private val exercisePart = combine(repository.exercises, repository.family, exerciseFilter) { exercises, family, filter ->
    ExercisePart(exercises, family, filter, exerciseRows(exercises, family, filter))
  }

  val state: StateFlow<BrowseState> =
    combine(tunePart, exercisePart, repository.transposition, repository.seedError) { tunes, ex, transposition, error ->
        BrowseState(
          transposition = transposition,
          family = ex.family,
          tunes = tunes.rows,
          totalTunes = tunes.all.size,
          exercises = ex.rows,
          totalExercises = exerciseRows(ex.all, ex.family).size,
          tuneFilter = tunes.filter,
          exerciseFilter = ex.filter,
          styles = tunes.all.mapNotNull { it.style }.distinct().sorted(),
          categories = ex.all.map { it.category }.distinct().sortedBy { Phase.categoryRank(it) },
          seedError = error,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BrowseState())

  val actions = TuneActions(
    addToWishlist = { launch { practice.addToWishlist(it) } },
    removeFromMyTunes = { launch { practice.removeFromMyTunes(it) } },
    startLearning = { launch { practice.startLearning(it) } },
    markKnown = { launch { practice.markKnown(it) } },
    retire = { launch { practice.retire(it) } },
    restore = { launch { practice.restore(it) } },
    setKeyPolicy = { id, policy -> launch { practice.setKeyPolicy(id, policy) } },
    setComfort = { id, comfort -> launch { practice.setComfort(id, comfort) } },
    toggleKnownKey = { id, pc -> launch { practice.toggleKnownKey(id, pc) } },
  )

  fun updateTuneFilter(change: (TuneFilter) -> TuneFilter) = tuneFilter.update(change)

  fun updateExerciseFilter(change: (ExerciseFilter) -> ExerciseFilter) = exerciseFilter.update(change)

  fun selectTransposition(transposition: Transposition) = repository.setTransposition(transposition)

  fun selectFamily(family: Family?) = repository.setFamily(family)

  private fun launch(block: suspend () -> Unit) {
    viewModelScope.launch { block() }
  }
}
