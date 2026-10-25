package com.larakelley.woodshed.ui.tools

import android.app.Application
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.larakelley.woodshed.audio.DroneState
import com.larakelley.woodshed.domain.ChordQuality
import com.larakelley.woodshed.domain.Transposition
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DroneScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun notesAreNamedForTheHornAndSetInConcertPitch() {
    val picked = mutableListOf<Int>()
    compose.setContent { DroneScreen(DroneState(pitchClass = 10), Transposition.Bb, 440, DroneActions(setPitchClass = { picked += it })) }
    // Concert Bb reads as C on a Bb horn.
    compose.onNodeWithTag("drone-note").assertTextEquals("C")
    compose.onNodeWithText("Concert Bb", substring = true).assertExists()
    // Picking a written D asks for concert C.
    compose.onNodeWithTag("drone-note-2").performScrollTo().performClick()
    assertEquals(0, Math.floorMod(picked.single(), 12))
  }

  @Test
  fun chordsAreNamedAndSpelledForTheHorn() {
    val picked = mutableListOf<ChordQuality>()
    compose.setContent {
      DroneScreen(DroneState(pitchClass = 8, chord = ChordQuality.DOMINANT7), Transposition.Bb, 440, DroneActions(setChord = { picked += it }))
    }
    // Concert Ab7 is written Bb7 on a Bb horn.
    compose.onNodeWithTag("drone-note").assertTextEquals("Bb7")
    compose.onNodeWithText("Bb D F Ab. Concert Ab7", substring = true).assertExists()
    compose.onNodeWithTag("drone-chord-minor7").performScrollTo().performClick()
    assertEquals(listOf(ChordQuality.MINOR7), picked)
    // Voicing shows only for chords it changes.
    compose.onNodeWithTag("drone-voicing-shell").assertExists()
  }

  @Test
  fun voicingIsHiddenForASingleNote() {
    compose.setContent { DroneScreen(DroneState(chord = ChordQuality.NOTE), Transposition.C, 440, DroneActions()) }
    compose.onNodeWithTag("drone-voicing-shell").assertDoesNotExist()
  }
}
