package com.larakelley.woodshed.ui.practice

import android.app.Application
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.larakelley.woodshed.domain.Focus
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.PlanBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PracticeScreenTest {
  @get:Rule val compose = createComposeRule()

  private val card = CardView(
    title = "Autumn Leaves",
    key = "Written A minor",
    exercise = "Changes without the melody",
    instructions = "Arpeggiate the changes in time with no melody.",
    shape = "AABC, 32 bars, Swing",
    buttons = listOf(Grade.AGAIN to "10m", Grade.HARD to "1d", Grade.GOOD to "1d", Grade.EASY to "4d"),
  )

  private val tuneStep = StepView.Tune(card, minutes = 10)

  /** The step timer ticks forever, so a test clock that advances itself until idle would never stop. */
  private fun session(ui: PracticeUi, onGrade: (Grade) -> Unit = {}, onNext: () -> Unit = {}) {
    compose.mainClock.autoAdvance = false
    compose.setContent { SessionScreen(ui, onGrade = onGrade, onNext = onNext, onStop = {}) }
    compose.mainClock.advanceTimeByFrame()
  }

  @Test
  fun tuneStepShowsTheCardAndReportsTheGrade() {
    val graded = mutableListOf<Grade>()
    session(PracticeUi(running = true, step = tuneStep, stepCount = 3), onGrade = { graded += it })

    compose.onNodeWithTag("card-title").assertExists()
    compose.onNodeWithText("Written A minor").assertExists()
    compose.onNodeWithText("1 of 3").assertExists()
    compose.onNodeWithTag("grade-good").performClick()
    assertEquals(listOf(Grade.GOOD), graded)
  }

  @Test
  fun exerciseStepIsFinishedWithDone() {
    var next = 0
    val step = StepView.Exercise("Long tones", "Tone", "Hold each note.", tempoBpm = 60, minutes = 5)
    session(PracticeUi(running = true, step = step, stepIndex = 1, stepCount = 3), onNext = { next++ })

    compose.onNodeWithText("Tone, 60 bpm").assertExists()
    compose.onNodeWithText("2 of 3").assertExists()
    compose.onNodeWithTag("step-timer").assertExists()
    compose.onNodeWithTag("grade-good").assertDoesNotExist()
    compose.onNodeWithTag("step-done").performClick()
    assertEquals(1, next)
  }

  @Test
  fun finishedSessionSummarises() {
    session(PracticeUi(running = true, step = null, stepCount = 4, reviewedThisSession = 2))
    compose.onNodeWithText("4 blocks, 2 tunes reviewed.").assertExists()
  }

  @Test
  fun setupListsTunesByGroupAndReportsChoices() {
    val tunes = listOf(SessionTune(1, "Solar", TuneGroup.DUE), SessionTune(2, "Afro Blue", TuneGroup.LEARNING), SessionTune(3, "Nardis", TuneGroup.WISHLIST))
    val toggled = mutableListOf<Any>()
    val actions = SetupActions(toggleTune = { toggled += it }, toggleFocus = { toggled += it }, setMinutes = { toggled += it })
    compose.setContent { PracticeHome(PracticeUi(setup = SetupUi(minutes = 30, tunes = tunes, selected = setOf(1))), actions) }

    compose.onNodeWithTag("focus-speed").performClick()
    compose.onNodeWithTag("length-45").performClick()
    compose.onNodeWithText("Tunes (1 picked)").assertExists()
    // The tune list sits below the fold on a test-sized screen.
    val list = compose.onNode(hasScrollAction())
    list.performScrollToNode(hasText("Due for review"))
    list.performScrollToNode(hasText("Wishlist"))
    list.performScrollToNode(hasTestTag("pick-3"))
    compose.onNodeWithTag("pick-3").performClick()
    assertEquals(listOf<Any>(Focus.SPEED, 45, 3L), toggled)
  }

  @Test
  fun planShowsBlocksAndTotalAndCanBeEdited() {
    val plan = listOf(
      PlanItem(PlanBlock.Exercise(1, 5), "Long tones", "Tone"),
      PlanItem(PlanBlock.Tune(7, 2, 25), "Solar", "Guide-tone solo"),
    )
    val swapped = mutableListOf<Int>()
    var started = false
    compose.setContent { PracticeHome(PracticeUi(setup = SetupUi(plan = plan)), SetupActions(swap = { swapped += it }, start = { started = true })) }

    compose.onNodeWithText("30 min planned").assertExists()
    compose.onNodeWithText("Tune: Guide-tone solo").assertExists()
    compose.onNodeWithTag("swap-1").performClick()
    compose.onNodeWithTag("start").performClick()
    assertEquals(listOf(1), swapped)
    assertTrue(started)
  }
}
