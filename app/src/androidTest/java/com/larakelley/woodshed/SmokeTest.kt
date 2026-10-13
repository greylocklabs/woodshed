package com.larakelley.woodshed

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app end to end on a real Android system: the bundled data loading, a practice session with its foreground
 * notification, the audio tools starting and stopping, and adding a tune. What Robolectric cannot run lives here.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SmokeTest {
  @get:Rule(order = 0)
  val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECORD_AUDIO)

  @get:Rule(order = 1)
  val compose = createAndroidComposeRule<MainActivity>()

  private fun openTab(name: String) {
    compose.onNodeWithTag("tab-$name").performScrollTo().performClick()
  }

  @Test
  fun theBundledTunesLoadAndCanBeSearched() {
    openTab("Tunes")
    compose.onNodeWithTag("search").performTextInput("Solar")
    // The first launch imports the seed in the background, so the result can take a while to appear.
    compose.waitUntilAtLeastOneExists(hasText("Solar") and hasAnyAncestor(hasTestTag("tune-list")), timeoutMillis = 60_000)
  }

  @Test
  fun aSessionRunsWithItsNotification() {
    compose.waitUntilAtLeastOneExists(hasTestTag("instrument-tenor_sax"), timeoutMillis = 30_000)
    compose.onNodeWithTag("instrument-tenor_sax").performClick()
    compose.onNodeWithTag("length-15").performClick()
    compose.onNodeWithTag("build-plan").performClick()
    compose.waitUntilAtLeastOneExists(hasTestTag("start"), timeoutMillis = 10_000)
    compose.onNodeWithTag("start").performClick()
    compose.waitUntilAtLeastOneExists(hasText("1 of", substring = true), timeoutMillis = 10_000)

    // Test system images have no notification shade to open, so the notification is read from the system.
    val notifications = compose.activity.getSystemService(NotificationManager::class.java)
    val deadline = System.currentTimeMillis() + 10_000
    fun shown() = notifications.activeNotifications.any { it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.startsWith("Block 1 of") == true }
    while (!shown() && System.currentTimeMillis() < deadline) Thread.sleep(200)
    assertTrue("the session notification shows the block", shown())

    compose.onNodeWithTag("end-session").performClick()
    compose.onNodeWithTag("confirm-end").performClick()
    compose.waitUntilAtLeastOneExists(hasTestTag("build-plan"), timeoutMillis = 10_000)
  }

  @Test
  fun theDroneAndTunerStartAndStop() {
    openTab("Drone")
    compose.onNodeWithTag("drone-toggle").performClick()
    compose.onNodeWithText("◼ Stop").assertExists()
    compose.onNodeWithTag("drone-toggle").performClick()
    compose.onNodeWithText("▶ Start drone").assertExists()

    openTab("Tuner")
    compose.onNodeWithTag("tuner-toggle").performClick()
    compose.onNodeWithText("◼ Stop").assertExists()
    compose.onNodeWithTag("tuner-toggle").performClick()
  }

  @Test
  fun aTuneCanBeAdded() {
    openTab("Tunes")
    compose.waitUntilAtLeastOneExists(hasTestTag("add-tune"), timeoutMillis = 60_000)
    compose.onNodeWithTag("add-tune").performClick()
    compose.onNodeWithTag("editor-title").performTextInput("Woodshed Blues")
    compose.onNodeWithTag("editor-save").performScrollTo().performClick()
    compose.waitUntilAtLeastOneExists(hasText("Woodshed Blues"), timeoutMillis = 10_000)
  }
}
