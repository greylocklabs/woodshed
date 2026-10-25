package com.larakelley.woodshed.audio

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.domain.bars
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OneSoundTest {
  private val prefs = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("one-sound-test", Context.MODE_PRIVATE).also {
    it.edit().clear().commit()
  }
  private val metronome = Metronome(prefs)
  private val drone = Drone(prefs) { 440 }
  private val changes = ChangesPlayer(prefs, metronome, drone) { 440 }.also { player ->
    // As the app wires them.
    metronome.onStart = player::stop
    drone.onStart = player::stop
  }

  @After
  fun tearDown() {
    changes.stop()
    metronome.stop()
    drone.stop()
  }

  private fun playChanges() {
    changes.load(ChangesSource("form:test", "form:test", "Test") { bars("I | IV") })
    changes.start()
    assertTrue(changes.state.value.playing)
  }

  @Test
  fun startingTheDroneOrMetronomeStopsTheChanges() {
    playChanges()
    drone.start()
    assertFalse(changes.state.value.playing)
    assertTrue(drone.state.value.playing)

    playChanges()
    // The changes bring their own click and drone, so starting them stops both.
    assertFalse(drone.state.value.playing)
    metronome.start()
    assertFalse(changes.state.value.playing)
    assertTrue(metronome.state.value.running)
  }

  @Test
  fun eachStartRunsItsHookEvenWhenAlreadyPlaying() {
    var starts = 0
    drone.onStart = { starts++ }
    metronome.onStart = { starts++ }
    drone.start()
    drone.start()
    metronome.start()
    assertEquals(3, starts)
  }

  @Test
  fun restartingKeepsAStop() {
    playChanges()
    changes.stop()
    changes.restart()
    assertFalse(changes.state.value.playing)
  }
}
