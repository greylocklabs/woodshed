package com.larakelley.woodshed.session

import com.larakelley.woodshed.ui.practice.StepView
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionNotificationsTest {
  @Test
  fun changesAreDoneLikeAnExercise() {
    assertEquals("Done", SessionNotifications.nextLabel(StepView.Exercise("Long tones", "Tone", "", null, 5)))
    assertEquals("Done", SessionNotifications.nextLabel(StepView.Changes("Blues", "G", "form:blues", 5, 6)))
    assertEquals("Skip", SessionNotifications.nextLabel(StepView.Solo("Tangerine", "Chet Baker", null, null, "", null, emptyList(), 7)))
    assertEquals("Blues in G", SessionNotifications.title(StepView.Changes("Blues", "G", "form:blues", 5, 6)))
  }
}
