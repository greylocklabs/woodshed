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
import com.larakelley.woodshed.domain.Focus
import com.larakelley.woodshed.domain.Instrument
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
  val year: Int? = null,
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

data class ExerciseRow(
  val name: String,
  val category: String,
  val summary: String,
  val description: String,
  /** "All instruments", or the families it is written for. */
  val instruments: String = "All instruments",
  val minutes: Int = 0,
  val difficulty: Int = 0,
  val tempoBpm: Int? = null,
  val appliesToTune: Boolean = false,
  /** Session focus areas that draw on this exercise's category. */
  val focus: List<String> = emptyList(),
)

fun exerciseRow(e: ExerciseEntity): ExerciseRow {
  val tempo = e.tempoBpm?.let { ", $it bpm" } ?: ""
  return ExerciseRow(
    name = e.name,
    category = e.category,
    summary = "${e.category}, ${e.minutes} min, difficulty ${e.difficulty}$tempo",
    description = e.description,
    instruments = if (e.families.isEmpty()) "All instruments" else e.families.joinToString(", ") { it.seedName },
    minutes = e.minutes,
    difficulty = e.difficulty,
    tempoBpm = e.tempoBpm,
    appliesToTune = e.appliesToTune,
    focus = Focus.entries.filter { e.category in it.categories }.map { it.label },
  )
}

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
  val sort: TuneSort = TuneSort.TITLE,
)

/** Tune list orders. Ties fall back to fake-book title order, and unknown values sort last. */
enum class TuneSort(val label: String) {
  TITLE("Title"),
  COMPOSER("Composer"),
  YEAR("Year"),
  KEY("Key"),
  LAST_PRACTICED("Last practiced"),
  COMFORT("Comfort"),
}

data class ExerciseFilter(
  val query: String = "",
  val category: String? = null,
  /** Show only exercises that suit this instrument; null shows all. */
  val instrument: Instrument? = null,
  val sort: ExerciseSort = ExerciseSort.CATEGORY,
)

/** Exercise list orders; category follows the session's phase order. */
enum class ExerciseSort(val label: String) {
  CATEGORY("Category"),
  NAME("Name"),
  MINUTES("Minutes"),
  DIFFICULTY("Difficulty"),
}

data class BrowseState(
  val transposition: Transposition = Transposition.C,
  val tunes: List<TuneRow> = emptyList(),
  val totalTunes: Int = 0,
  val exercises: List<ExerciseRow> = emptyList(),
  val totalExercises: Int = 0,
  /** The instruments the user plays; the exercise filter offers only these. Empty means every instrument. */
  val myInstruments: List<Instrument> = Instrument.entries,
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
      year = s.year,
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
  sortTunes(rows, filter.sort).filter { row ->
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

/** [rows] arrive in title order, so a stable sort keeps title order within ties. */
fun sortTunes(rows: List<TuneRow>, sort: TuneSort): List<TuneRow> = when (sort) {
  TuneSort.TITLE -> rows
  TuneSort.COMPOSER -> rows.sortedWith(compareBy(nullsLast()) { it.composer?.lowercase() })
  TuneSort.YEAR -> rows.sortedWith(compareBy(nullsLast()) { it.year })
  TuneSort.KEY -> rows.sortedWith(compareBy<TuneRow, Int?>(nullsLast()) { it.homeKey?.pitchClass }.thenBy { it.tonality })
  TuneSort.LAST_PRACTICED -> rows.sortedWith(compareByDescending<TuneRow, Long?>(nullsFirst()) { it.state?.lastReviewedAt })
  TuneSort.COMFORT -> rows.sortedWith(compareByDescending<TuneRow, Comfort?>(nullsFirst()) { it.knowledge?.comfort })
}

/**
 * Exercises for [filter]'s instrument, or with none chosen, for any of [mine]; an empty [mine] keeps everything.
 * An instrument the user no longer plays counts as none chosen.
 */
fun exerciseRows(exercises: List<ExerciseEntity>, filter: ExerciseFilter = ExerciseFilter(), mine: Collection<Instrument> = emptyList()): List<ExerciseRow> {
  val chosen = filter.instrument?.takeIf { mine.isEmpty() || it in mine }
  return exercises
    .filter { e -> if (chosen != null) chosen.suits(e.families, e.category) else mine.isEmpty() || mine.any { it.suits(e.families, e.category) } }
    .filter { filter.category == null || it.category == filter.category }
    .filter { matchesQuery(filter.query, it.name, it.description) }
    .sortedWith(
      when (filter.sort) {
        ExerciseSort.CATEGORY -> compareBy({ Phase.categoryRank(it.category) }, { it.id })
        ExerciseSort.NAME -> compareBy { it.name.lowercase() }
        ExerciseSort.MINUTES -> compareBy({ it.minutes }, { it.name.lowercase() })
        ExerciseSort.DIFFICULTY -> compareBy({ it.difficulty }, { it.name.lowercase() })
      }
    )
    .map(::exerciseRow)
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
  private val exerciseFilter = MutableStateFlow(ExerciseFilter(instrument = repository.instrument.value))

  private val library = combine(repository.standards, repository.tuneStates, repository.tuneKnowledge, repository.transposition) {
      standards, states, knowledge, transposition ->
    tuneRows(standards, transposition, states.associateBy { it.standardId }, knowledge.associateBy { it.standardId })
  }

  private data class TunePart(val all: List<TuneRow>, val filter: TuneFilter, val rows: List<TuneRow>)

  private data class ExercisePart(val all: List<ExerciseEntity>, val filter: ExerciseFilter, val mine: List<Instrument>, val rows: List<ExerciseRow>)

  private val tunePart = combine(library, tuneFilter) { rows, filter -> TunePart(rows, filter, filterTunes(rows, filter)) }

  private val exercisePart = combine(repository.exercises, exerciseFilter, repository.myInstruments) { exercises, filter, mine ->
    val offered = Instrument.entries.filter { mine.isEmpty() || it in mine }
    val shown = filter.copy(instrument = filter.instrument?.takeIf { it in offered })
    ExercisePart(exercises, shown, offered, exerciseRows(exercises, shown, mine))
  }

  val state: StateFlow<BrowseState> =
    combine(tunePart, exercisePart, repository.transposition, repository.seedError) { tunes, ex, transposition, error ->
        BrowseState(
          transposition = transposition,
          tunes = tunes.rows,
          totalTunes = tunes.all.size,
          exercises = ex.rows,
          totalExercises = exerciseRows(ex.all, mine = ex.mine).size,
          myInstruments = ex.mine,
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

  private fun launch(block: suspend () -> Unit) {
    viewModelScope.launch { block() }
  }
}
