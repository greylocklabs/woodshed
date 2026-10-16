package com.larakelley.woodshed.ui.settings

import android.app.Application
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
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
  fun generalTabSetsAppearance() {
    val picked = mutableListOf<Any>()
    compose.setContent { SettingsScreen(SettingsUi(), SettingsActions(setThemeMode = { picked += it }), onBack = {}) }
    compose.onNodeWithTag("theme-dark").performClick()
    compose.onNodeWithTag("key-bb").assertDoesNotExist()
    assertEquals(listOf<Any>(ThemeMode.DARK), picked)
  }

  @Test
  fun instrumentsTabTogglesTheOnesIPlayAndOffersThemForNow() {
    val toggled = mutableListOf<Pair<Instrument, Boolean>>()
    val now = mutableListOf<Instrument>()
    val ui = SettingsUi(instrument = Instrument.ALTO_SAX, myInstruments = setOf(Instrument.ALTO_SAX, Instrument.FLUTE))
    compose.setContent { SettingsScreen(ui, SettingsActions(setInstrument = { now += it }, toggleMyInstrument = { i, on -> toggled += i to on }), onBack = {}) }

    compose.onNodeWithTag("tab-instruments").performClick()
    compose.onNodeWithTag("now-flute").performClick()
    compose.onNodeWithTag("now-trumpet").assertDoesNotExist()
    compose.onNodeWithTag("plays-alto_sax").performScrollTo().assertIsOn()
    compose.onNodeWithTag("plays-french_horn").performScrollTo().assertIsOff().performClick()
    compose.onNodeWithTag("plays-piano").performScrollTo().assertExists()
    assertEquals(listOf(Instrument.FLUTE), now)
    assertEquals(listOf(Instrument.FRENCH_HORN to true), toggled)
  }

  @Test
  fun backupTabAsksBeforeReplacingData() {
    compose.setContent { SettingsScreen(SettingsUi(message = "Backup saved."), SettingsActions(), onBack = {}) }
    compose.onNodeWithTag("tab-backup").performClick()
    compose.onNodeWithText("Not backed up from this phone yet").assertExists()
    compose.onNodeWithTag("backup-message").assertExists()
    compose.onNodeWithTag("restore").performClick()
    compose.onNodeWithText("Restore from a backup?").assertExists()
    compose.onNodeWithText("Cancel").performClick()
    compose.onNodeWithText("Restore from a backup?").assertDoesNotExist()
  }

  @Test
  fun aboutTabCreditsTheAuthorAndOffersDonating() {
    var closed = false
    compose.setContent { SettingsScreen(SettingsUi(version = "0.1"), SettingsActions(), onBack = { closed = true }) }
    compose.onNodeWithTag("tab-about").performClick()
    compose.onNodeWithTag("made-by").assertExists()
    compose.onNodeWithText("Lara Kelley", substring = true).assertExists()
    compose.onNodeWithTag("donate").assertExists()
    compose.onNodeWithTag("review").assertExists()
    compose.onNodeWithTag("source").assertExists()
    compose.onNodeWithTag("changelog").assertTextContains("changelog", substring = true)
    compose.onNodeWithText("buy me a coffee", substring = true).assertExists()
    // Play requires the privacy policy to be reachable from inside the app.
    compose.onNodeWithTag("privacy").performScrollTo().assertTextContains("privacy policy", substring = true)
    compose.onNodeWithTag("close-settings").performClick()
    assertEquals(true, closed)
  }
}
