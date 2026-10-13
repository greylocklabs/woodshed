package com.larakelley.woodshed.ui.tools

import android.app.Application
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.larakelley.woodshed.audio.DroneState
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
}
