package com.larakelley.woodshed

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.larakelley.woodshed.data.WhatsNew
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The What's new dialog after an update, read from the changelog bundled in the real build. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WhatsNewTest {
  @get:Rule val compose = createEmptyComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
  private val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName!!

  @After
  fun tearDown() = prefs.edit(commit = true) { putString(WhatsNew.SEEN, version) }

  @Test
  fun anUpdateShowsTheChangelogOnceUntilDismissed() {
    // As if the last version opened was older than every release.
    prefs.edit(commit = true) { putString(WhatsNew.SEEN, "0.0.1") }
    ActivityScenario.launch(MainActivity::class.java).use {
      compose.waitUntilAtLeastOneExists(hasTestTag("whats-new"), timeoutMillis = 10_000)
      compose.onNodeWithTag("whats-new-done").performClick()
      compose.waitUntilDoesNotExist(hasTestTag("whats-new"), timeoutMillis = 5_000)
    }
    ActivityScenario.launch(MainActivity::class.java).use {
      compose.waitUntilAtLeastOneExists(hasTestTag("tab-Tunes"), timeoutMillis = 10_000)
      compose.onNodeWithTag("whats-new").assertDoesNotExist()
    }
  }
}
