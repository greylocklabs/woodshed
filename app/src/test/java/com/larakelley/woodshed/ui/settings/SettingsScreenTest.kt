package com.larakelley.woodshed.ui.settings

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.larakelley.woodshed.data.ResetPart
import com.larakelley.woodshed.data.ThemeMode
import com.larakelley.woodshed.domain.Instrument
import com.larakelley.woodshed.ui.browse.DEFAULT_TABS
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
  fun generalTabMovesTabsAndResetsTheirOrder() {
    val moves = mutableListOf<Pair<String, Int>>()
    var reset = false
    val ui = SettingsUi(tabOrder = listOf("Tunes") + DEFAULT_TABS.filter { it != "Tunes" })
    compose.setContent { SettingsScreen(ui, SettingsActions(moveTab = { n, by -> moves += n to by }, resetTabs = { reset = true }), onBack = {}) }
    compose.onNodeWithTag("tab-up-Tunes").performScrollTo().assertIsNotEnabled()
    compose.onNodeWithTag("tab-down-Tunes").performClick()
    compose.onNodeWithTag("tab-up-Practice").performScrollTo().performClick()
    compose.onNodeWithTag("reset-tabs").performScrollTo().performClick()
    assertEquals(listOf("Tunes" to 1, "Practice" to -1), moves)
    assertEquals(true, reset)
  }

  @Test
  fun moreThanThreeInstrumentsPickTheDefaultFromADropdown() {
    val picked = mutableListOf<Instrument>()
    val mine = setOf(Instrument.ALTO_SAX, Instrument.TENOR_SAX, Instrument.TRUMPET, Instrument.FLUTE)
    compose.setContent {
      SettingsScreen(SettingsUi(instrument = Instrument.ALTO_SAX, myInstruments = mine), SettingsActions(setInstrument = { picked += it }), onBack = {})
    }
    compose.onNodeWithTag("tab-instruments").performClick()
    compose.onNodeWithText("Default instrument", substring = true, ignoreCase = true).assertExists()
    compose.onNodeWithTag("now-alto_sax").assertDoesNotExist()
    compose.onNodeWithTag("chip-Instrument").performClick()
    // The menu's item, not the Trumpet switch further down the tab.
    compose.onNode(hasText("Trumpet") and hasAnyAncestor(isPopup())).performClick()
    assertEquals(listOf(Instrument.TRUMPET), picked)
  }

  @Test
  fun holdingATabAndDraggingItMovesIt() {
    val set = mutableListOf<List<String>>()
    compose.setContent { SettingsScreen(SettingsUi(), SettingsActions(setTabOrder = { set += it }), onBack = {}) }
    val row = compose.onNodeWithTag("tab-order-Practice").performScrollTo()
    val height = row.fetchSemanticsNode().size.height.toFloat()
    // Hold, then drag down a little over two rows: Practice lands third.
    row.performTouchInput {
      down(center)
      advanceEventTime(1_000)
      repeat(10) { moveBy(Offset(0f, height * 0.22f)) }
      up()
    }
    assertEquals(listOf(listOf("Random", "History", "Practice") + DEFAULT_TABS.drop(3)), set)
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
  fun backupTabClearsOnlyWhatIsChosenAfterAsking() {
    val cleared = mutableListOf<Set<ResetPart>>()
    compose.setContent { SettingsScreen(SettingsUi(), SettingsActions(reset = { cleared += it }), onBack = {}) }
    compose.onNodeWithTag("tab-backup").performClick()
    compose.onNodeWithTag("reset").performScrollTo().assertIsNotEnabled()
    compose.onNodeWithTag("reset-history").performScrollTo().performClick()
    compose.onNodeWithTag("reset-material").performScrollTo().performClick()
    // Clearing the user's own material says what goes with it.
    compose.onNodeWithTag("reset-knock-on").assertExists()
    compose.onNodeWithTag("reset").performScrollTo().performClick()
    compose.onNodeWithText("Clear practice history, my material?").assertExists()
    compose.onNodeWithTag("confirm-reset").performClick()
    assertEquals(listOf(setOf(ResetPart.HISTORY, ResetPart.MATERIAL)), cleared)
    // Everything turns every part on.
    compose.onNodeWithTag("reset-everything").performScrollTo().performClick()
    compose.onNodeWithTag("reset").performScrollTo().performClick()
    compose.onNodeWithTag("confirm-reset").performClick()
    assertEquals(ResetPart.entries.toSet(), cleared.last())
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
