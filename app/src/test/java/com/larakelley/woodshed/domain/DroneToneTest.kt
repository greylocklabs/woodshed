package com.larakelley.woodshed.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DroneToneTest {
  @Test
  fun pitchesAreTunedToTheReference() {
    assertEquals(440.0, midiHz(69), 1e-9)
    assertEquals(261.626, midiHz(60), 1e-3)
    assertEquals(442.0, midiHz(69, a4 = 442.0), 1e-9)
    assertEquals(221.0, DroneRange.MIDDLE.rootHz(9, a4 = 442.0), 1e-9)
  }

  @Test
  fun everyDroneInARangeSitsInTheSameOctave() {
    // Written notes on every instrument, so a Bb horn's C (concert Bb) sits where a C instrument's C does.
    for (range in DroneRange.entries) {
      val lowest = midiHz(range.lowestMidi)
      val highest = midiHz(range.lowestMidi + 11)
      for (transposition in Transposition.entries) {
        for (written in 0..11) {
          val concert = Math.floorMod(written - transposition.offset, 12)
          val root = range.rootHz(concert)
          assertTrue("$range $transposition written $written: $root Hz", root in lowest..highest)
        }
      }
    }
    assertEquals(listOf(DroneRange.LOW, DroneRange.MIDDLE, DroneRange.HIGH), DroneRange.entries.sortedBy { it.lowestMidi })
    // Ranges meet without overlapping: the top of each is a half step under the next.
    DroneRange.entries.zipWithNext { below, above -> assertEquals(above.lowestMidi, below.lowestMidi + 12) }
  }

  @Test
  fun itFadesInStaysUnderFullScaleAndFadesOut() {
    val tone = DroneTone(48_000)
    val buffer = ShortArray(2_048)
    // The first buffer starts from silence, so a start never clicks.
    tone.render(buffer, 220.0, 1.0, listOf(0, 7))
    assertTrue(abs(buffer[0].toInt()) < 50)
    var peak = 0
    repeat(20) {
      tone.render(buffer, 220.0, 1.0, listOf(0, 7))
      peak = maxOf(peak, buffer.maxOf { abs(it.toInt()) })
    }
    assertTrue("peak $peak", peak in 10_000 until Short.MAX_VALUE.toInt())
    assertEquals(0.0, tone.render(buffer, 220.0, 0.0, listOf(0, 7)), 1e-9)
    assertTrue(abs(buffer.last().toInt()) < 50)
  }

  @Test
  fun itSoundsAtTheAskedPitch() {
    val tone = DroneTone(48_000)
    val buffer = ShortArray(48_000)
    tone.render(ShortArray(2_048), 110.0, 1.0, listOf(0))
    tone.render(buffer, 110.0, 1.0, listOf(0))
    // The overtones are all in phase with the root, so the waveform crosses upward once a cycle.
    val upward = (1 until buffer.size).count { buffer[it - 1] < 0 && buffer[it] >= 0 }
    assertEquals(110.0, upward.toDouble(), 2.0)
  }

  @Test
  fun aChordIsNoLouderThanARootAndFifth() {
    fun peak(notes: List<Int>): Int {
      val tone = DroneTone(48_000)
      val buffer = ShortArray(2_048)
      var peak = 0
      repeat(40) {
        tone.render(buffer, 110.0, 1.0, notes)
        peak = maxOf(peak, buffer.maxOf { abs(it.toInt()) })
      }
      return peak
    }
    val fifth = peak(listOf(0, 7))
    val thirteenth = peak(voice(ChordQuality.THIRTEEN, Voicing.SPREAD))
    assertTrue("13th $thirteenth against fifth $fifth", thirteenth <= fifth * 1.05 && thirteenth < Short.MAX_VALUE)
  }
}
