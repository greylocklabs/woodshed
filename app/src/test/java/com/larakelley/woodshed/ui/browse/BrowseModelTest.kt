package com.larakelley.woodshed.ui.browse

import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.TuneKnowledgeEntity
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.domain.Comfort
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Instrument
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.Tonality
import com.larakelley.woodshed.domain.Transposition
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseModelTest {
  @Test
  fun keyLabelTransposesForTheHorn() {
    assertEquals("C", keyLabel(Key(3, false), Tonality.MAJOR, Transposition.Eb))
    assertEquals("Concert Eb", keyLabel(Key(3, false), Tonality.MAJOR, Transposition.C))
    assertEquals("Concert C minor", keyLabel(Key(0, true), Tonality.MINOR, Transposition.C))
  }

  @Test
  fun keyLabelKeepsATonalityWhoseKeyIsUnknown() {
    assertEquals("Minor, key unknown", keyLabel(null, Tonality.MINOR, Transposition.Bb))
    assertEquals("Key unknown", keyLabel(null, null, Transposition.Bb))
  }

  @Test
  fun tunesSortLikeAFakeBookIndex() {
    val titles = listOf("The Nearness Of You", "'Round Midnight", "A Night In Tunisia", "Moanin'", "Autumn Leaves")
    val rows = tuneRows(titles.mapIndexed { i, t -> standard(i, t) }, Transposition.C)
    assertEquals(listOf("Autumn Leaves", "Moanin'", "The Nearness Of You", "A Night In Tunisia", "'Round Midnight"), rows.map { it.title })
  }

  @Test
  fun exercisesFilterByInstrumentAndKeepUnscopedOnes() {
    val exercises = listOf(
      exercise(1, "Long tones", emptyList()),
      exercise(2, "Overtone series", listOf(Family.SAXOPHONE)),
      exercise(3, "Lip slurs", listOf(Family.BRASS)),
    )
    assertEquals(listOf("Long tones", "Overtone series"), exerciseRows(exercises, ExerciseFilter(instrument = Instrument.TENOR_SAX)).map { it.name })
    assertEquals(listOf("Long tones", "Lip slurs"), exerciseRows(exercises, ExerciseFilter(instrument = Instrument.TROMBONE)).map { it.name })
    assertEquals(3, exerciseRows(exercises).size)
    // A piano has no family of its own and does not blow, so tone work drops out too.
    assertEquals(emptyList<String>(), exerciseRows(exercises, ExerciseFilter(instrument = Instrument.PIANO)).map { it.name })
  }

  @Test
  fun styleIsDroppedWhenItRepeatsTheForm() {
    val blues = standard(1, "Blue Monk").copy(form = "Blues", bars = 12, style = "Blues")
    val ballad = standard(2, "Lush Life").copy(form = "AABA", bars = 32, style = "Ballad")
    assertEquals(listOf("Blues, 12 bars", "AABA, 32 bars, Ballad"), tuneRows(listOf(blues, ballad), Transposition.C).map { it.shape })
  }

  @Test
  fun searchIgnoresCasePunctuationAndWordOrder() {
    val rows = tuneRows(listOf(standard(1, "'Round Midnight").copy(composer = "Thelonious Monk"), standard(2, "Autumn Leaves")), Transposition.C)
    assertEquals(listOf("'Round Midnight"), filterTunes(rows, TuneFilter(query = "round midnight")).map { it.title })
    assertEquals(listOf("'Round Midnight"), filterTunes(rows, TuneFilter(query = "monk")).map { it.title })
    assertEquals(listOf("'Round Midnight"), filterTunes(rows, TuneFilter(query = "midnight ROUND")).map { it.title })
    assertEquals(2, filterTunes(rows, TuneFilter(query = "  ")).size)
  }

  @Test
  fun statusStyleAndComfortFilters() {
    val tunes = listOf(standard(1, "A").copy(style = "Ballad"), standard(2, "B"), standard(3, "C"))
    val states = mapOf(1L to TuneStateEntity(1, Lifecycle.LEARNING), 2L to TuneStateEntity(2, Lifecycle.WISHLIST))
    val knowledge = mapOf(1L to TuneKnowledgeEntity(1, comfort = Comfort.COMFORTABLE), 3L to TuneKnowledgeEntity(3, comfort = Comfort.SHAKY))
    val rows = tuneRows(tunes, Transposition.C, states, knowledge)
    fun titles(f: TuneFilter) = filterTunes(rows, f).map { it.title }
    assertEquals(listOf("A", "B"), titles(TuneFilter(status = StatusFilter.MINE)))
    assertEquals(listOf("C"), titles(TuneFilter(status = StatusFilter.LIBRARY)))
    assertEquals(listOf("B"), titles(TuneFilter(status = StatusFilter.WISHLIST)))
    assertEquals(listOf("A"), titles(TuneFilter(style = "Ballad")))
    assertEquals(listOf("A"), titles(TuneFilter(minComfort = Comfort.GETTING_THERE)))
    assertEquals(listOf("A", "C"), titles(TuneFilter(minComfort = Comfort.SHAKY)))
  }

  @Test
  fun collectionFilter() {
    val tunes = listOf(standard(1, "A").copy(collection = "Trad"), standard(2, "B"), standard(3, "C"))
    val rows = tuneRows(tunes, Transposition.C)
    assertEquals(listOf("A"), filterTunes(rows, TuneFilter(collection = "Trad")).map { it.title })
    assertEquals(listOf("B", "C"), filterTunes(rows, TuneFilter(collection = "Standards")).map { it.title })
  }

  @Test
  fun tonalityFilter() {
    val tunes = listOf(standard(1, "A").copy(tonality = Tonality.MINOR), standard(2, "B").copy(tonality = Tonality.MAJOR), standard(3, "C"))
    val rows = tuneRows(tunes, Transposition.C)
    assertEquals(listOf("A"), filterTunes(rows, TuneFilter(tonality = Tonality.MINOR)).map { it.title })
    assertEquals(listOf("B"), filterTunes(rows, TuneFilter(tonality = Tonality.MAJOR)).map { it.title })
  }

  @Test
  fun unknownTimeSignatureIsLeftOutOfTheShape() {
    val row = tuneRows(listOf(standard(1, "A").copy(timeSignature = null, bars = 32, style = "Swing")), Transposition.C).single()
    assertEquals("32 bars, Swing", row.shape)
  }

  @Test
  fun exerciseSearchAndCategory() {
    val exercises = listOf(exercise(1, "Long tones", emptyList()), exercise(2, "Lip slurs", listOf(Family.BRASS)).copy(category = "Technique"))
    assertEquals(listOf("Lip slurs"), exerciseRows(exercises, ExerciseFilter(category = "Technique")).map { it.name })
    assertEquals(listOf("Long tones"), exerciseRows(exercises, ExerciseFilter(query = "tones")).map { it.name })
  }

  private fun standard(id: Int, title: String) =
    StandardEntity(id.toLong(), "s$id", title, null, null, null, "4/4", null, null, null, null, null)

  @Test
  fun exerciseRowNamesItsInstrumentsAndFocus() {
    val all = exerciseRow(exercise(1, "Long tones", emptyList()))
    assertEquals("All instruments", all.instruments)
    assertEquals(listOf("Tone"), all.focus)
    val reeds = exerciseRow(exercise(2, "Mouthpiece pitch bends", listOf(Family.SAXOPHONE, Family.CLARINET)).copy(category = "Technique"))
    assertEquals("Saxophone, Clarinet", reeds.instruments)
    assertEquals(listOf("Speed"), reeds.focus)
  }

  @Test
  fun tunesSortByEachOrderWithUnknownsLast() {
    val tunes = listOf(
      standard(1, "Alpha").copy(composer = "Zed", year = 1950, keyPitchClass = 7, tonality = Tonality.MAJOR),
      standard(2, "Bravo").copy(composer = "Abe", year = null, keyPitchClass = 0, tonality = Tonality.MINOR),
      standard(3, "Charlie").copy(composer = null, year = 1930, keyPitchClass = null),
    )
    val states = mapOf(1L to TuneStateEntity(1, Lifecycle.LEARNING, lastReviewedAt = 5), 3L to TuneStateEntity(3, Lifecycle.LEARNING, lastReviewedAt = 9))
    val knowledge = mapOf(2L to TuneKnowledgeEntity(2, comfort = Comfort.SOLID), 1L to TuneKnowledgeEntity(1, comfort = Comfort.SHAKY))
    val rows = tuneRows(tunes, Transposition.C, states, knowledge)
    fun order(sort: TuneSort) = filterTunes(rows, TuneFilter(sort = sort)).map { it.title }
    assertEquals(listOf("Alpha", "Bravo", "Charlie"), order(TuneSort.TITLE))
    assertEquals(listOf("Bravo", "Alpha", "Charlie"), order(TuneSort.COMPOSER))
    assertEquals(listOf("Charlie", "Alpha", "Bravo"), order(TuneSort.YEAR))
    assertEquals(listOf("Bravo", "Alpha", "Charlie"), order(TuneSort.KEY))
    assertEquals(listOf("Charlie", "Alpha", "Bravo"), order(TuneSort.LAST_PRACTICED))
    assertEquals(listOf("Bravo", "Alpha", "Charlie"), order(TuneSort.COMFORT))
  }

  @Test
  fun exercisesSortByNameMinutesAndDifficulty() {
    val exercises = listOf(
      exercise(1, "Scales", emptyList()).copy(minutes = 10, difficulty = 3, category = "Scales & Patterns"),
      exercise(2, "Air", emptyList()).copy(minutes = 5, difficulty = 1, category = "Air & Breathing"),
      exercise(3, "Bends", emptyList()).copy(minutes = 8, difficulty = 2),
    )
    fun order(sort: ExerciseSort) = exerciseRows(exercises, ExerciseFilter(sort = sort)).map { it.name }
    assertEquals(listOf("Bends", "Air", "Scales"), order(ExerciseSort.CATEGORY))
    assertEquals(listOf("Air", "Bends", "Scales"), order(ExerciseSort.NAME))
    assertEquals(listOf("Air", "Bends", "Scales"), order(ExerciseSort.MINUTES))
    assertEquals(listOf("Air", "Bends", "Scales"), order(ExerciseSort.DIFFICULTY))
  }

  private fun exercise(id: Long, name: String, families: List<Family>) =
    ExerciseEntity(id, name, "Tone", "", 5, 1, null, families, false)
}
