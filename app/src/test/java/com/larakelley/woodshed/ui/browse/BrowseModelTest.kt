package com.larakelley.woodshed.ui.browse

import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Instrument
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseModelTest {
  @Test
  fun keyLabelTransposesForTheHorn() {
    assertEquals("C", keyLabel("Eb", Instrument.ALTO_SAX))
    assertEquals("Concert Eb", keyLabel("Eb", null))
    assertEquals("Concert C minor", keyLabel("C-", null))
  }

  @Test
  fun keyLabelShowsRawTextRatherThanGuessing() {
    assertEquals("Concert Eb, C", keyLabel("Eb, C", Instrument.TENOR_SAX))
    assertEquals("Key unknown", keyLabel(null, Instrument.TENOR_SAX))
  }

  @Test
  fun tunesSortLikeAFakeBookIndex() {
    val titles = listOf("The Nearness Of You", "'Round Midnight", "A Night In Tunisia", "Moanin'", "Autumn Leaves")
    val rows = tuneRows(titles.mapIndexed { i, t -> standard(i, t) }, null)
    assertEquals(listOf("Autumn Leaves", "Moanin'", "The Nearness Of You", "A Night In Tunisia", "'Round Midnight"), rows.map { it.title })
  }

  @Test
  fun exercisesFilterByFamilyAndKeepUnscopedOnes() {
    val exercises = listOf(
      exercise(1, "Long tones", emptyList()),
      exercise(2, "Overtone series", listOf(Family.SAXOPHONE)),
      exercise(3, "Lip slurs", listOf(Family.BRASS)),
    )
    assertEquals(listOf("Long tones", "Overtone series"), exerciseRows(exercises, Instrument.TENOR_SAX).map { it.name })
    assertEquals(listOf("Long tones", "Lip slurs"), exerciseRows(exercises, Instrument.TROMBONE).map { it.name })
    assertEquals(3, exerciseRows(exercises, null).size)
  }

  private fun standard(id: Int, title: String) =
    StandardEntity(id.toLong(), "s$id", title, null, null, null, "4/4", null, null, null, null, null)

  private fun exercise(id: Long, name: String, families: List<Family>) =
    ExerciseEntity(id, name, "Tone", "", 5, 1, null, families, false)
}
