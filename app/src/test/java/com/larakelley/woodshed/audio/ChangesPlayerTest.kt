package com.larakelley.woodshed.audio

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.domain.bars
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChangesPlayerTest {
  private val prefs = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("changes-test", Context.MODE_PRIVATE).also {
    it.edit().clear().commit()
  }

  private fun player() = ChangesPlayer(prefs, Metronome(prefs), Drone(prefs) { 440 }) { 440 }

  private fun source(keyId: String, id: String = keyId) = ChangesSource(id, keyId, keyId) { bars("I") }

  @Test
  fun eachItemKeepsItsOwnKey() {
    val player = player()
    player.load(source("form:blues"))
    player.setKey("form:blues", 5)
    player.load(source("form:rhythm-changes"))
    // Never set, it takes the key played last.
    assertEquals(5, player.state.value.key)
    player.setKey("form:rhythm-changes", 10)
    // Set from another screen without loading it, a key waits for its item.
    player.setKey("pattern:circle", 3)
    assertEquals(10, player.state.value.key)
    player.load(source("form:blues"))
    assertEquals(5, player.state.value.key)
    // A pattern's settings change its id but not where its key is kept, and keys outlast the player.
    val again = player()
    again.load(source("pattern:circle", "pattern:circle:FOURTHS:7"))
    assertEquals(3, again.state.value.key)
    assertEquals(mapOf("form:blues" to 5, "form:rhythm-changes" to 10, "pattern:circle" to 3), again.state.value.keys)
  }
}
