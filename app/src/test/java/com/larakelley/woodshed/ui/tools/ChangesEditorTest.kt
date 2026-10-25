package com.larakelley.woodshed.ui.tools

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.larakelley.woodshed.data.MyChanges
import com.larakelley.woodshed.domain.Bar
import com.larakelley.woodshed.domain.bars
import com.larakelley.woodshed.domain.numerals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChangesEditorTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun barsAreWrittenChordByChord() {
    var saved: Pair<String, List<Bar>>? = null
    // In F, so the root chips are named from F.
    compose.setContent { ChangesEditor(null, 5, onSave = { name, bars -> saved = name to bars }, onCancel = {}) }
    // A name is needed first.
    compose.onNodeWithTag("changes-save").performScrollTo().performClick()
    compose.onNodeWithTag("changes-problem").assertExists()
    compose.onNodeWithTag("changes-name").performTextInput("Turnaround")
    // Bar 2 becomes a Gm7, split, with its second half a C7.
    compose.onNodeWithTag("changes-bar-1").performClick()
    compose.onNodeWithTag("changes-more-chords").performScrollTo().performClick()
    compose.onNodeWithTag("changes-root-2").performScrollTo().performClick()
    compose.onNodeWithTag("changes-quality-minor7").performScrollTo().performClick()
    compose.onNodeWithTag("changes-split").performScrollTo().performClick()
    compose.onNodeWithText("Second half").performScrollTo().performClick()
    compose.onNodeWithTag("changes-root-7").performScrollTo().performClick()
    compose.onNodeWithTag("changes-quality-dominant7").performScrollTo().performClick()
    compose.onNodeWithTag("changes-remove").performScrollTo().performClick()
    compose.onNodeWithTag("changes-save").performScrollTo().performClick()
    assertEquals("Turnaround", saved?.first)
    // Removing took out the selected bar, the split one, leaving the other three.
    assertEquals("I | I | I", saved?.second?.let(::numerals))
  }

  @Test
  fun aSplitBarKeepsBothHalves() {
    var saved: List<Bar>? = null
    compose.setContent {
      ChangesEditor(MyChanges(1, "Two", bars("I")), 0, onSave = { _, bars -> saved = bars }, onCancel = {}, onDelete = {})
    }
    compose.onNodeWithTag("changes-split").performScrollTo().performClick()
    compose.onNodeWithText("Second half").performScrollTo().performClick()
    compose.onNodeWithTag("changes-more-chords").performScrollTo().performClick()
    compose.onNodeWithTag("changes-root-7").performScrollTo().performClick()
    compose.onNodeWithTag("changes-quality-dominant7").performScrollTo().performClick()
    compose.onNodeWithTag("changes-save").performScrollTo().performClick()
    assertEquals("I V7", saved?.let(::numerals))
  }

  @Test
  fun deletingAsksFirst() {
    var deleted = false
    compose.setContent { ChangesEditor(MyChanges(1, "Two", bars("I")), 0, onSave = { _, _ -> }, onCancel = {}, onDelete = { deleted = true }) }
    compose.onNodeWithTag("changes-delete").performScrollTo().performClick()
    assertTrue(!deleted)
    compose.onNodeWithTag("changes-delete-confirm").performClick()
    assertTrue(deleted)
  }
}
