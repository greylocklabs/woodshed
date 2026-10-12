package com.larakelley.woodshed.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.Phase
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.domain.titleSortKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TuneRow(val slug: String, val title: String, val credit: String, val shape: String, val key: String)

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

fun tuneRows(standards: List<StandardEntity>, transposition: Transposition): List<TuneRow> =
  standards.sortedBy { titleSortKey(it.title) }.map { s ->
    TuneRow(
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

class BrowseViewModel(private val repository: WoodshedRepository) : ViewModel() {
  val state: StateFlow<BrowseState> =
    combine(repository.standards, repository.exercises, repository.transposition, repository.family, repository.seedError) {
        standards, exercises, transposition, family, error ->
        BrowseState(transposition, family, tuneRows(standards, transposition), exerciseRows(exercises, family), error)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BrowseState())

  fun selectTransposition(transposition: Transposition) = repository.setTransposition(transposition)

  fun selectFamily(family: Family?) = repository.setFamily(family)
}
