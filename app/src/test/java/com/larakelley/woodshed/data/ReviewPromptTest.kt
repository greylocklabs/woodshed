package com.larakelley.woodshed.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReviewPromptTest {
  private val prefs = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("review-prompt-test", Context.MODE_PRIVATE)
  private val day = 24 * 60 * 60 * 1000L
  private var now = 30 * day
  private val prompt = ReviewPrompt(prefs, installedAt = 0L, now = { now }, zone = ZoneOffset.UTC)

  /** Practice logged at noon on each of [days] days before now. */
  private fun practiceOn(vararg days: Int) = days.map { now - it * day + day / 2 }

  @Before
  fun setUp() {
    prefs.edit().clear().commit()
  }

  @Test
  fun asksOnceUsedOnThreeDaysAWeekAfterInstalling() {
    assertFalse("two days is not enough", prompt.shouldAsk(practiceOn(1, 2, 2)))
    assertTrue(prompt.shouldAsk(practiceOn(1, 2, 3)))
  }

  @Test
  fun waitsAWeekAfterInstalling() {
    now = 6 * day
    assertFalse(prompt.shouldAsk(practiceOn(1, 2, 3)))
  }

  @Test
  fun waitsMonthsBeforeAskingAgain() {
    prompt.asked()
    now += 89 * day
    assertFalse(prompt.shouldAsk(practiceOn(1, 2, 3)))
    now += 2 * day
    assertTrue(prompt.shouldAsk(practiceOn(1, 2, 3)))
  }
}
