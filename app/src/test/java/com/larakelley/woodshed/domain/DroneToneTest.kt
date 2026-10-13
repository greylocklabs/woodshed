package com.larakelley.woodshed.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DroneToneTest {
  @Test
  fun pitchesAreTunedToTheReference() {
    assertEquals(440.0, pitchHz(9, 4), 1e-9)
    assertEquals(261.626, pitchHz(0, 4), 1e-3)
    assertEquals(130.813, pitchHz(0, 3), 1e-3)
    assertEquals(442.0, pitchHz(9, 4, a4 = 442.0), 1e-9)
  }

  @Test
  fun itFadesInStaysUnderFullScaleAndFadesOut() {
    val tone = DroneTone(48_000)
    val buffer = ShortArray(2_048)
    // The first buffer starts from silence, so a start never clicks.
    tone.render(buffer, 220.0, 1.0, fifth = true)
    assertTrue(abs(buffer[0].toInt()) < 50)
    var peak = 0
    repeat(20) {
      tone.render(buffer, 220.0, 1.0, fifth = true)
      peak = maxOf(peak, buffer.maxOf { abs(it.toInt()) })
    }
    assertTrue("peak $peak", peak in 10_000 until Short.MAX_VALUE.toInt())
    assertEquals(0.0, tone.render(buffer, 220.0, 0.0, fifth = true), 1e-9)
    assertTrue(abs(buffer.last().toInt()) < 50)
  }

  @Test
  fun itSoundsAtTheAskedPitch() {
    val tone = DroneTone(48_000)
    val buffer = ShortArray(48_000)
    tone.render(ShortArray(2_048), 110.0, 1.0, fifth = false)
    tone.render(buffer, 110.0, 1.0, fifth = false)
    // The overtones are all in phase with the root, so the waveform crosses upward once a cycle.
    val upward = (1 until buffer.size).count { buffer[it - 1] < 0 && buffer[it] >= 0 }
    assertEquals(110.0, upward.toDouble(), 2.0)
  }
}
