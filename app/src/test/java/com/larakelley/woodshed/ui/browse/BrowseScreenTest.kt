package com.larakelley.woodshed.ui.browse

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.larakelley.woodshed.data.db.ExerciseEntity
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Instrument
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
    StandardEntity(1, "body-and-soul", "Body And Soul", "Johnny Green", 1930, 32, "4/4", "AABA", "Ballad", "Db", 1, false),
    StandardEntity(2, "on-green-dolphin-street", "On Green Dolphin Street", null, 1947, 32, "4/4", "ABAC", "Swing", "Eb, C", null, null),
  )
  private val exercises = listOf(
    ExerciseEntity(1, "Long tones", "Tone", "Low to mid range.", 10, 2, 60, emptyList(), false),
    ExerciseEntity(2, "Lip slurs", "Tone", "Low partial to the next.", 10, 3, 72, listOf(Family.BRASS), false),
  )

  private fun showWithLiveState() {
    compose.setContent {
      var instrument by remember { mutableStateOf<Instrument?>(null) }
      BrowseScreen(
        BrowseState(instrument, tuneRows(standards, instrument), exerciseRows(exercises, instrument)),
        onSelectInstrument = { instrument = it },
      )
    }
  }

  @Test
  fun pickingAnInstrumentRewritesTheKeys() {
    showWithLiveState()
    compose.onNodeWithText("Concert Db").assertExists()

    compose.onNodeWithTag("instrument-picker").performClick()
    compose.onNodeWithText("Tenor sax").performClick()

    compose.onNodeWithText("Eb").assertExists()
    compose.onNodeWithText("Concert Db").assertDoesNotExist()
    // A key that does not parse stays visible as written rather than being transposed.
    compose.onNodeWithText("Concert Eb, C").assertExists()
  }

  @Test
  fun exercisesTabHidesOtherFamiliesWork() {
    showWithLiveState()
    compose.onNodeWithTag("instrument-picker").performClick()
    compose.onNodeWithText("Alto sax").performClick()
    compose.onNodeWithText("Exercises (1)").performClick()

    compose.onNodeWithText("Long tones").assertExists()
    compose.onNodeWithText("Lip slurs").assertDoesNotExist()
  }

  @Test
  fun seedErrorIsShownNotSwallowed() {
    compose.setContent { BrowseScreen(BrowseState(seedError = "duplicate slug: x"), onSelectInstrument = {}) }
    compose.onNodeWithText("Could not load the bundled data: duplicate slug: x").assertExists()
  }
}
