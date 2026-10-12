package com.larakelley.woodshed.ui.browse

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.larakelley.woodshed.data.ExerciseDraft
import com.larakelley.woodshed.domain.Family
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExerciseEditorTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun aTakenNameIsRefusedThenANewOneSaves() {
    val saved = mutableListOf<ExerciseDraft>()
    compose.setContent { ExerciseEditor(null, listOf("Tone", "Technique"), setOf("long tones"), onSave = { saved += it }, onDelete = {}, onReset = {}, onBack = {}) }
    compose.onNodeWithTag("exercise-name").performTextInput("Long Tones")
    compose.onNodeWithTag("exercise-description").performTextInput("Hold each note.")
    compose.onNodeWithTag("exercise-save").performScrollTo().performClick()
    compose.onNodeWithText("Another exercise already has that name.").performScrollTo().assertExists()
    assertTrue(saved.isEmpty())

    compose.onNodeWithTag("exercise-name").performScrollTo().performTextInput(" for brass")
    compose.onNodeWithTag("exercise-family-brass").performScrollTo().performClick()
    compose.onNodeWithTag("exercise-save").performScrollTo().performClick()
    assertEquals("Long Tones for brass", saved.single().name)
    assertEquals(setOf(Family.BRASS), saved.single().families)
  }
}
