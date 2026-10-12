package com.larakelley.woodshed.ui.practice

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.larakelley.woodshed.data.PracticeSummary
import com.larakelley.woodshed.domain.Grade
import org.junit.Assert.assertEquals
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

  @Test
  fun reviewShowsTheCardAndReportsTheGrade() {
    val graded = mutableListOf<Grade>()
    compose.setContent { ReviewScreen(PracticeUi(card = card, reviewing = true), onGrade = { graded += it }, onStop = {}) }

    compose.onNodeWithTag("card-title").assertExists()
    compose.onNodeWithText("Written A minor").assertExists()
    compose.onNodeWithText("Changes without the melody").assertExists()
    compose.onNodeWithTag("grade-good").performClick()
    assertEquals(listOf(Grade.GOOD), graded)
  }

  @Test
  fun emptySessionSaysDone() {
    compose.setContent { ReviewScreen(PracticeUi(card = null, reviewing = true, reviewedThisSession = 3), onGrade = {}, onStop = {}) }
    compose.onNodeWithText("3 reviewed. Nothing else is due.").assertExists()
  }

  @Test
  fun homeOffersStartOnlyWhenThereIsSomethingToDo() {
    compose.setContent { PracticeHome(PracticeUi(summary = PracticeSummary(due = 0, newToday = 0, wishlist = 0, reviewedToday = 0)), {}, {}) }
    compose.onNodeWithText("Start").assertDoesNotExist()
  }

  @Test
  fun homeOffersStartWhenTunesAreDue() {
    compose.setContent { PracticeHome(PracticeUi(summary = PracticeSummary(due = 2, newToday = 0, wishlist = 0, reviewedToday = 0)), {}, {}) }
    compose.onNodeWithText("Start").assertExists()
    compose.onNodeWithTag("due-count").assertExists()
  }
}
