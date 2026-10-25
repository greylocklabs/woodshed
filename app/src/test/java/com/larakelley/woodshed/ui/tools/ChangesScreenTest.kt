package com.larakelley.woodshed.ui.tools

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.larakelley.woodshed.audio.ChangesSource
import com.larakelley.woodshed.audio.ChangesState
import com.larakelley.woodshed.audio.DroneState
import com.larakelley.woodshed.audio.MetronomeState
import com.larakelley.woodshed.data.MyChanges
import com.larakelley.woodshed.domain.ChangesLibrary
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.domain.bars
import com.larakelley.woodshed.domain.numerals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChangesScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun aFormLoadsAndPlaysInTheHornsKey() {
    val loaded = mutableListOf<ChangesSource>()
    val keys = mutableListOf<Pair<String, Int>>()
    compose.setContent {
      ChangesScreen(
        ChangesState(key = 0, keys = mapOf("form:blues" to 3)),
        MetronomeState(),
        DroneState(),
        Transposition.Bb,
        ChangesActions(load = { loaded += it }, setKey = { id, key -> keys += id to key }),
      )
    }
    compose.onNodeWithTag("changes-blues").performClick()
    compose.waitForIdle()
    assertEquals("form:blues", loaded.single().keyId)
    // The blues keeps its own key: concert Eb, written F on a Bb horn.
    compose.onNodeWithText("Key: F").assertExists()
    compose.onNodeWithText("Concert Eb", substring = true).assertExists()
    compose.onNodeWithTag("changes-chord").assertTextEquals("F7")
    compose.onNodeWithText("Key: F").performClick()
    compose.onNodeWithText("G").performClick()
    // Written G on a Bb horn is concert F.
    assertEquals(listOf("form:blues" to 5), keys)
    compose.onNodeWithText("12 bars").assertExists()
  }

  @Test
  fun theClickCanBeTurnedOff() {
    var click: Boolean? = null
    compose.setContent {
      ChangesScreen(
        ChangesState(source = "form:blues", bars = ChangesLibrary.form("blues")!!.bars),
        MetronomeState(),
        DroneState(),
        Transposition.C,
        ChangesActions(setClick = { click = it }),
      )
    }
    compose.onNodeWithTag("changes-blues").performClick()
    // The pills scroll sideways inside the page, so the page scrolls to them first.
    compose.onNodeWithText("Accent beat 1").performScrollTo()
    compose.onNodeWithTag("changes-click-off").performClick()
    assertEquals(false, click)
  }

  @Test
  fun aPatternLoadsBeforeItStarts() {
    val calls = mutableListOf<String>()
    compose.setContent {
      ChangesScreen(ChangesState(), MetronomeState(), DroneState(), Transposition.C, ChangesActions(load = { calls += it.id }, toggle = { calls += "start" }))
    }
    compose.onNodeWithTag("changes-circle").performScrollTo().performClick()
    compose.onNodeWithText("Bb7").assertExists()
    compose.onNodeWithTag("pattern-start").performScrollTo().performClick()
    assertTrue(calls.toString(), calls.size == 2 && calls[0].startsWith("pattern:circle:") && calls[1] == "start")
  }

  @Test
  fun commonTemposSetTheChangesTempo() {
    val bpm = mutableListOf<Int>()
    val beats = mutableListOf<Int>()
    compose.setContent {
      ChangesScreen(
        ChangesState(source = "form:blues", bars = ChangesLibrary.form("blues")!!.bars),
        MetronomeState(),
        DroneState(),
        Transposition.C,
        ChangesActions(setBpm = { bpm += it }, setBeatsPerBar = { beats += it }),
      )
    }
    compose.onNodeWithTag("changes-blues").performClick()
    compose.onNodeWithTag("changes-bpm").assertTextEquals("120")
    compose.onNodeWithTag("changes-preset-jazz_waltz").performScrollTo().performClick()
    assertEquals(listOf(160), bpm)
    // A jazz waltz is in three.
    assertEquals(listOf(3), beats)
  }

  @Test
  fun changesLeftPlayingCanBeStoppedFromTheList() {
    var stopped = false
    val blues = ChangesLibrary.form("blues")!!.bars
    compose.setContent {
      var open by remember { mutableStateOf(true) }
      if (open) {
        ChangesScreen(
          ChangesState(playing = true, source = "form:blues", title = "12-bar blues", bars = blues, bar = 4, beat = 0),
          MetronomeState(),
          DroneState(),
          Transposition.C,
          ChangesActions(toggle = { stopped = true }),
        )
      }
    }
    // Back on the tab while it plays, the player shows; back from it, the list shows what plays.
    compose.onNodeWithTag("changes-chord").assertTextEquals("F7")
    compose.onNodeWithText("Back to changes").performClick()
    compose.onNodeWithText("F7, bar 5 of 12").assertExists()
    compose.onNodeWithTag("now-playing-stop", useUnmergedTree = true).performClick()
    assertTrue(stopped)
  }

  @Test
  fun yourOwnChangesAreWrittenPlayedEditedAndDeleted() {
    val saved = mutableListOf<Triple<Long?, String, String>>()
    val deleted = mutableListOf<Long>()
    val loaded = mutableListOf<String>()
    var mine by mutableStateOf(emptyList<MyChanges>())
    compose.setContent {
      ChangesScreen(
        ChangesState(),
        MetronomeState(),
        DroneState(),
        Transposition.C,
        ChangesActions(
          load = { loaded += it.id },
          save = { id, name, bars -> saved += Triple(id, name, numerals(bars)) },
          delete = { deleted += it },
        ),
        mine,
      )
    }
    compose.onNodeWithTag("changes-new").performScrollTo().performClick()
    compose.onNodeWithTag("changes-name").performTextInput("Vamp")
    compose.onNodeWithTag("changes-save").performScrollTo().performClick()
    assertEquals(Triple(null, "Vamp", "I | I | I | I"), saved.single())
    // Saved, it shows under Yours and plays.
    mine = listOf(MyChanges(7, "Vamp", bars("I | IV")))
    compose.onNodeWithTag("changes-mine-7").performScrollTo().performClick()
    compose.waitForIdle()
    assertTrue(loaded.single().startsWith("mine:7:"))
    // Edited, it loads again; deleted, the list shows.
    compose.onNodeWithTag("changes-edit").performClick()
    compose.onNodeWithTag("changes-save").performScrollTo().performClick()
    assertEquals(Triple(7L, "Vamp", "I | IV"), saved.last())
    mine = listOf(MyChanges(7, "Vamp", bars("I | IV | V")))
    compose.waitForIdle()
    assertEquals(2, loaded.size)
    compose.onNodeWithTag("changes-edit").performClick()
    compose.onNodeWithTag("changes-delete").performScrollTo().performClick()
    compose.onNodeWithTag("changes-delete-confirm").performClick()
    assertEquals(listOf(7L), deleted)
    mine = emptyList()
    compose.onNodeWithTag("changes-new").performScrollTo().assertExists()
  }

  @Test
  fun aPatternIsSavedAsYoursOneTimeThrough() {
    val saved = mutableListOf<Pair<String, Int>>()
    compose.setContent {
      ChangesScreen(ChangesState(), MetronomeState(), DroneState(), Transposition.C, ChangesActions(save = { _, name, bars -> saved += name to bars.size }))
    }
    compose.onNodeWithTag("changes-circle").performScrollTo().performClick()
    compose.onNodeWithTag("pattern-save").performClick()
    assertEquals(listOf("Around the circle" to 24), saved)
    compose.onNodeWithTag("changes-list").assertExists()
  }
}
