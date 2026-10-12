package com.larakelley.woodshed.ui.browse

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.larakelley.woodshed.data.TuneDraft
import com.larakelley.woodshed.domain.Tonality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TuneEditorTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun addingNeedsATitleThenSavesTheDraft() {
    val saved = mutableListOf<TuneDraft>()
    compose.setContent { TuneEditor(null, onSave = { saved += it }, onDelete = {}, onReset = {}, onBack = {}) }

    compose.onNodeWithTag("editor-save").performScrollTo().performClick()
    assertTrue(saved.isEmpty())
    compose.onNodeWithText("A title is needed.").assertExists()

    compose.onNodeWithTag("editor-title").performScrollTo().performTextInput("Blues for Woody")
    // The pill rows scroll sideways inside the scrolling form, and scrolling to a pill only moves its own row,
    // so bring the form down to the key and tonality rows first.
    compose.onNodeWithText("METER").performScrollTo()
    compose.onNodeWithTag("editor-tonality-minor").performClick()
    compose.onNodeWithTag("editor-key-eb").performScrollTo().performClick()
    compose.onNodeWithTag("editor-save").performScrollTo().performClick()
    assertEquals(1, saved.size)
    assertEquals("Blues for Woody", saved.single().title)
    assertEquals("Eb", saved.single().keyName)
    assertEquals(Tonality.MINOR, saved.single().tonality)
  }

  private fun row(userCreated: Boolean, edited: Boolean) = TuneRow(
    id = 1, slug = "x", title = "Solar", composer = null, credit = "", shape = "", style = null, key = "", homeKey = null, tonality = null,
    userCreated = userCreated, edited = edited, draft = TuneDraft(title = "Solar"),
  )

  @Test
  fun yourTunesCanBeDeletedAndEditedOnesReset() {
    var deleted = 0
    compose.setContent { TuneEditor(row(userCreated = true, edited = false), onSave = {}, onDelete = { deleted++ }, onReset = {}, onBack = {}) }
    compose.onAllNodesWithTag("editor-reset").fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
    compose.onNodeWithTag("editor-delete").performScrollTo().performClick()
    compose.onNodeWithTag("editor-confirm").performClick()
    assertEquals(1, deleted)
  }

  @Test
  fun anEditedSeedTuneOffersReset() {
    var reset = 0
    compose.setContent { TuneEditor(row(userCreated = false, edited = true), onSave = {}, onDelete = {}, onReset = { reset++ }, onBack = {}) }
    compose.onNodeWithTag("editor-title").performTextReplacement("Solar (Miles)")
    compose.onNodeWithTag("editor-reset").performScrollTo().performClick()
    compose.onNodeWithTag("editor-confirm").performClick()
    assertEquals(1, reset)
  }
}
