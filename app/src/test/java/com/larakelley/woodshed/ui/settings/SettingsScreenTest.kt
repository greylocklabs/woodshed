package com.larakelley.woodshed.ui.settings

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.larakelley.woodshed.data.ThemeMode
import com.larakelley.woodshed.domain.Instrument
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun choicesAreReportedAndBackupStateIsShown() {
    val picked = mutableListOf<Any?>()
    val actions = SettingsActions(setInstrument = { picked += it }, setThemeMode = { picked += it })
    compose.setContent { SettingsScreen(SettingsUi(message = "Backup saved."), actions, onBack = {}) }

    compose.onNodeWithTag("instrument-trumpet").performScrollTo().performClick()
    compose.onNodeWithTag("theme-DARK").performScrollTo().performClick()
    assertEquals(listOf<Any?>(Instrument.TRUMPET, ThemeMode.DARK), picked)
    compose.onNodeWithText("Not backed up from this phone yet").performScrollTo().assertExists()
    compose.onNodeWithTag("backup-message").assertExists()
  }

  @Test
  fun restoreAsksBeforeReplacingData() {
    compose.setContent { SettingsScreen(SettingsUi(), SettingsActions(), onBack = {}) }
    compose.onNodeWithTag("restore").performScrollTo().performClick()
    compose.onNodeWithText("Restore from a backup?").assertExists()
    compose.onNodeWithText("Cancel").performClick()
    compose.onNodeWithText("Restore from a backup?").assertDoesNotExist()
  }
}
