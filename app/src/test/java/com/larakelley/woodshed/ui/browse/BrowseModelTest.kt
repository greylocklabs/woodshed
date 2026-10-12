package com.larakelley.woodshed.ui.browse

import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.TuneKnowledgeEntity
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.domain.Comfort
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.Transposition
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseModelTest {
  @Test
  fun keyLabelTransposesForTheHorn() {
    assertEquals("C", keyLabel("Eb", Transposition.Eb))
    assertEquals("Concert Eb", keyLabel("Eb", Transposition.C))
    assertEquals("Concert C minor", keyLabel("C-", Transposition.C))
  }

  @Test
  fun keyLabelShowsRawTextRatherThanGuessing() {
    assertEquals("Concert Eb, C", keyLabel("Eb, C", Transposition.Bb))
    assertEquals("Key unknown", keyLabel(null, Transposition.Bb))
  }

  @Test
  fun tunesSortLikeAFakeBookIndex() {
    val titles = listOf("The Nearness Of You", "'Round Midnight", "A Night In Tunisia", "Moanin'", "Autumn Leaves")
    val rows = tuneRows(titles.mapIndexed { i, t -> standard(i, t) }, Transposition.C)
    assertEquals(listOf("Autumn Leaves", "Moanin'", "The Nearness Of You", "A Night In Tunisia", "'Round Midnight"), rows.map { it.title })
  }

  @Test
  fun exercisesFilterByFamilyAndKeepUnscopedOnes() {
    val exercises = listOf(
      exercise(1, "Long tones", emptyList()),
      exercise(2, "Overtone series", listOf(Family.SAXOPHONE)),
      exercise(3, "Lip slurs", listOf(Family.BRASS)),
    )
    assertEquals(listOf("Long tones", "Overtone series"), exerciseRows(exercises, Family.SAXOPHONE).map { it.name })
    assertEquals(listOf("Long tones", "Lip slurs"), exerciseRows(exercises, Family.BRASS).map { it.name })
    assertEquals(3, exerciseRows(exercises, null).size)
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
  fun exerciseSearchAndCategory() {
    val exercises = listOf(exercise(1, "Long tones", emptyList()), exercise(2, "Lip slurs", listOf(Family.BRASS)).copy(category = "Technique"))
    assertEquals(listOf("Lip slurs"), exerciseRows(exercises, null, ExerciseFilter(category = "Technique")).map { it.name })
    assertEquals(listOf("Long tones"), exerciseRows(exercises, null, ExerciseFilter(query = "tones")).map { it.name })
  }

  private fun standard(id: Int, title: String) =
    StandardEntity(id.toLong(), "s$id", title, null, null, null, "4/4", null, null, null, null, null)

  private fun exercise(id: Long, name: String, families: List<Family>) =
    ExerciseEntity(id, name, "Tone", "", 5, 1, null, families, false)
}
