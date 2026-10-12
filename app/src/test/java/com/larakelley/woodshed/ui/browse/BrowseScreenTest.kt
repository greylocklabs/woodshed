package com.larakelley.woodshed.ui.browse

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.TuneKnowledgeEntity
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.domain.Comfort
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.KeyMask
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.Tonality
import com.larakelley.woodshed.domain.Transposition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BrowseScreenTest {
  @get:Rule val compose = createComposeRule()

  private val standards = listOf(
    StandardEntity(1, "body-and-soul", "Body And Soul", "Johnny Green", 1930, 32, "4/4", "AABA", "Ballad", "Db", 1, Tonality.MAJOR),
    StandardEntity(2, "on-green-dolphin-street", "On Green Dolphin Street", null, 1947, 32, "4/4", "ABAC", "Swing", null, null, null),
  )
  private val exercises = listOf(
    ExerciseEntity(1, "Long tones", "Tone", "Low to mid range.", 10, 2, 60, emptyList(), false),
    ExerciseEntity(2, "Lip slurs", "Tone", "Low partial to the next.", 10, 3, 72, listOf(Family.BRASS), false),
  )

  private fun showWithLiveState() {
    compose.setContent {
      var transposition by remember { mutableStateOf(Transposition.C) }
      var family by remember { mutableStateOf<Family?>(null) }
      var filter by remember { mutableStateOf(TuneFilter()) }
      val rows = tuneRows(standards, transposition)
      BrowseScreen(
        BrowseState(
          transposition, family, filterTunes(rows, filter), rows.size, exerciseRows(exercises, family), exercises.size, tuneFilter = filter,
        ),
        onSelectTransposition = { transposition = it },
        onSelectFamily = { family = it },
        onTuneFilter = { filter = it(filter) },
      )
    }
  }

  @Test
  fun pickingATranspositionRewritesTheKeys() {
    showWithLiveState()
    compose.onNodeWithText("Concert Db").assertExists()

    compose.onNodeWithTag("transposition-picker").performClick()
    compose.onNodeWithText("Bb").performClick()

    compose.onNodeWithText("Eb").assertExists()
    compose.onNodeWithText("Concert Db").assertDoesNotExist()
    compose.onNodeWithText("Key unknown").assertExists()
  }

  @Test
  fun familyFilterHidesOtherFamiliesWork() {
    showWithLiveState()
    compose.onNodeWithText("Exercises").performClick()
    compose.onNodeWithText("Lip slurs").assertExists()

    compose.onNodeWithText("Sax").performClick()

    compose.onNodeWithText("1 of 2 exercises").assertExists()
    compose.onNodeWithText("Long tones").assertExists()
    compose.onNodeWithText("Lip slurs").assertDoesNotExist()
  }

  @Test
  fun exerciseOpensToItsDetails() {
    showWithLiveState()
    compose.onNodeWithText("Exercises").performClick()
    compose.onNodeWithText("Lip slurs").performClick()

    compose.onNodeWithTag("exercise-instruments").assertExists()
    compose.onNodeWithText("Brass").assertExists()
    compose.onNodeWithText("72 bpm").assertExists()
    compose.onNodeWithText("Back to exercises").performClick()
    compose.onNodeWithText("Long tones").assertExists()
  }

  @Test
  fun searchNarrowsTheTuneList() {
    showWithLiveState()
    compose.onNodeWithTag("search").performTextInput("dolphin")
    compose.onNodeWithText("On Green Dolphin Street").assertExists()
    compose.onNodeWithText("Body And Soul").assertDoesNotExist()
    compose.onNodeWithText("1 of 2 tunes").assertExists()
  }

  @Test
  fun tuneDetailSetsComfortAndKeysAndConfirmsRemoval() {
    var state by mutableStateOf(TuneStateEntity(1, Lifecycle.LEARNING))
    var knowledge by mutableStateOf(TuneKnowledgeEntity(1))
    var removed = false
    compose.setContent {
      val rows = tuneRows(standards, Transposition.Bb, mapOf(1L to state), mapOf(1L to knowledge))
      BrowseScreen(
        BrowseState(Transposition.Bb, null, rows, rows.size),
        onSelectTransposition = {},
        onSelectFamily = {},
        actions = TuneActions(
          setComfort = { _, c -> knowledge = knowledge.copy(comfort = c) },
          toggleKnownKey = { _, pc -> knowledge = knowledge.copy(knownKeys = KeyMask.toggle(knowledge.knownKeys, pc)) },
          removeFromMyTunes = { removed = true },
        ),
      )
    }
    compose.onNodeWithText("Body And Soul").performClick()
    compose.onNodeWithTag("comfort-shaky").performScrollTo().performClick()
    assertEquals(Comfort.SHAKY, knowledge.comfort)

    // Body And Soul is in Db concert; on a Bb horn that key reads as Eb.
    compose.onNodeWithTag("key-1").performScrollTo().assertTextEquals("Eb").performClick()
    assertEquals(1 shl 1, knowledge.knownKeys)
    compose.onNodeWithText("Keys I know it in (1 of 12)").assertExists()

    compose.onNodeWithTag("remove").performScrollTo().performClick()
    assertFalse(removed)
    compose.onNodeWithTag("confirm-remove").performClick()
    assertTrue(removed)
  }

  @Test
  fun bulkTunesSayTheirDetailsAreUnchecked() {
    val bulk = StandardEntity(9, "the-river-seine", "The River Seine", null, null, null, null, null, null, null, null, null, collection = "Trad", verified = false)
    compose.setContent { BrowseScreen(BrowseState(tunes = tuneRows(listOf(bulk), Transposition.C), totalTunes = 1), {}, {}) }
    compose.onNodeWithText("The River Seine").performClick()
    compose.onNodeWithText("Trad repertoire").assertExists()
    compose.onNodeWithTag("unverified").assertExists()
    compose.onNodeWithText("Key unknown").assertExists()
  }

  @Test
  fun seedErrorIsShownNotSwallowed() {
    compose.setContent { BrowseScreen(BrowseState(seedError = "duplicate slug: x"), onSelectTransposition = {}, onSelectFamily = {}) }
    compose.onNodeWithText("Could not load the bundled data: duplicate slug: x").assertExists()
  }
}
