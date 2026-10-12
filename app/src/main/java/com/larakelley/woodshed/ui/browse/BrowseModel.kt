package com.larakelley.woodshed.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.domain.Instrument
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.Phase
import com.larakelley.woodshed.domain.titleSortKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TuneRow(val slug: String, val title: String, val credit: String, val shape: String, val key: String)

data class ExerciseRow(val name: String, val summary: String, val description: String)

data class BrowseState(
  val instrument: Instrument? = null,
  val tunes: List<TuneRow> = emptyList(),
  val exercises: List<ExerciseRow> = emptyList(),
  val seedError: String? = null,
)

/** The key to show for a tune: written for the horn, or concert, or the raw text when it does not parse. */
fun keyLabel(raw: String?, instrument: Instrument?): String {
  val key = Key.parse(raw) ?: return if (raw == null) "Key unknown" else "Concert $raw"
  return if (instrument == null) "Concert ${key.displayName}" else key.writtenFor(instrument).displayName
}

fun tuneRows(standards: List<StandardEntity>, instrument: Instrument?): List<TuneRow> =
  standards.sortedBy { titleSortKey(it.title) }.map { s ->
    TuneRow(
      slug = s.slug,
      title = s.title,
      credit = listOfNotNull(s.composer, s.year?.toString()).joinToString(", "),
      shape = listOfNotNull(s.form, s.bars?.let { "$it bars" }, s.timeSignature.takeIf { it != "4/4" }, s.style).joinToString(", "),
      key = keyLabel(s.commonKeyRaw, instrument),
    )
  }

fun exerciseRows(exercises: List<ExerciseEntity>, instrument: Instrument?): List<ExerciseRow> =
  exercises
    .filter { instrument == null || it.families.isEmpty() || instrument.family in it.families }
    .sortedWith(compareBy({ Phase.categoryRank(it.category) }, { it.id }))
    .map { e ->
      val tempo = e.tempoBpm?.let { ", $it bpm" } ?: ""
      ExerciseRow(e.name, "${e.category}, ${e.minutes} min, difficulty ${e.difficulty}$tempo", e.description)
    }

class BrowseViewModel(private val repository: WoodshedRepository) : ViewModel() {
  val state: StateFlow<BrowseState> =
    combine(repository.standards, repository.exercises, repository.instrument, repository.seedError) { standards, exercises, instrument, error ->
        BrowseState(instrument, tuneRows(standards, instrument), exerciseRows(exercises, instrument), error)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BrowseState())

  fun selectInstrument(instrument: Instrument?) = repository.setInstrument(instrument)
}
