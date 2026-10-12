package com.larakelley.woodshed.ui.tools

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.larakelley.woodshed.audio.MetronomeState
import com.larakelley.woodshed.audio.TunerState
import com.larakelley.woodshed.domain.Feel
import com.larakelley.woodshed.domain.NoteReading
import com.larakelley.woodshed.domain.Transposition
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ToolsScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun metronomeAppliesOptionsAndPresets() {
    val calls = mutableListOf<Any>()
    val actions = MetronomeActions(
      toggle = { calls += "toggle" },
      setBpm = { calls += it },
      setBeatsPerBar = { calls += "beats $it" },
      setFeel = { calls += it },
      setAccentFirst = { calls += "accent $it" },
    )
    compose.setContent { MetronomeScreen(MetronomeState(bpm = 132), actions) }

    compose.onNodeWithText("bpm").assertExists()
    compose.onNodeWithTag("metronome-toggle").performClick()
    // The options row scrolls sideways inside the scrolling page; bring the page down to it first.
    compose.onNodeWithTag("accent").performScrollTo()
    compose.onNodeWithTag("feel-2-and-4").performClick()
    compose.onNodeWithTag("accent").performClick()
    compose.onNodeWithText("COMMON TEMPOS").performScrollTo().assertExists()
    compose.onNodeWithTag("preset-jazz_waltz").performScrollTo().performClick()
    assertEquals(listOf<Any>("toggle", Feel.BACKBEAT, "accent false", 160, "beats 3"), calls)
  }

  @Test
  fun tunerShowsTheWrittenNoteAndHowFarOffItIs() {
    val a4 = mutableListOf<Int>()
    val concertC = NoteReading.of(261.63 * Math.pow(2.0, 12.0 / 1200))
    compose.setContent { TunerScreen(TunerState(listening = true, reading = concertC), Transposition.Bb, TunerActions(setA4 = { a4 += it })) }

    compose.onNodeWithText("Concert C", substring = true).assertExists()
    compose.onNodeWithText("12 cents sharp").assertExists()
    compose.onNodeWithTag("a4").performScrollTo().assertExists()
    compose.onNodeWithText("+").performScrollTo().performClick()
    assertEquals(listOf(441), a4)
  }

  @Test
  fun tunerSaysWhenInTune() {
    compose.setContent { TunerScreen(TunerState(listening = true, reading = NoteReading.of(440.0)), Transposition.C, TunerActions()) }
    compose.onNodeWithText("In tune ✨").assertExists()
  }
}
