package com.larakelley.woodshed.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetronomeToolsTest {
  @Test
  fun backbeatClicksOnlyOnTwoAndFour() {
    val track = ClickTrack(48_000, 120, 4, feel = Feel.BACKBEAT)
    assertEquals(listOf(false, true, false, true, false, true), (0L..5L).map(track::sounds))
    assertFalse((0L..7L).any(track::isAccent))
  }

  @Test
  fun downbeatOnlyClicksOncePerBar() {
    val track = ClickTrack(48_000, 120, 3, feel = Feel.DOWNBEAT)
    assertEquals(listOf(true, false, false, true, false, false), (0L..5L).map(track::sounds))
  }

  @Test
  fun silentBeatsRenderNoSound() {
    val track = ClickTrack(48_000, 120, 4, feel = Feel.BACKBEAT)
    val beat = ShortArray(24_000) // one beat at 120 bpm
    track.render(0, beat)
    assertTrue(beat.all { it == 0.toShort() })
    track.render(24_000, beat)
    assertTrue(beat.any { it != 0.toShort() })
  }

  @Test
  fun accentCanBeTurnedOff() {
    assertTrue(ClickTrack(48_000, 120, 4).isAccent(0))
    assertFalse(ClickTrack(48_000, 120, 4, accentFirst = false).isAccent(0))
  }

  @Test
  fun tapTempoAveragesRecentTapsAndStartsOverAfterAPause() {
    val taps = TapTempo()
    assertNull(taps.tap(0))
    assertEquals(120, taps.tap(500))
    assertEquals(120, taps.tap(1_000))
    assertEquals(100, taps.tap(10_000).let { taps.tap(10_600) })
  }

  @Test
  fun presetsSitInsideTheMetronomeRangeAndTheWaltzIsInThree() {
    assertTrue(TempoPreset.entries.all { it.bpm in ClickTrack.MIN_BPM..ClickTrack.MAX_BPM })
    assertEquals(3, TempoPreset.JAZZ_WALTZ.beatsPerBar)
    assertEquals(TempoPreset.MEDIUM_SWING, TempoPreset.nearest(135))
  }

  private fun tone(hz: Double, rate: Int = 44_100, n: Int = 4_096, noise: Double = 0.0): FloatArray {
    val r = Random(1)
    // A horn-like tone: a fundamental with weaker overtones, which a naive peak picker would misread.
    return FloatArray(n) { i ->
      val t = i.toDouble() / rate
      (0.6 * sin(2 * PI * hz * t) + 0.3 * sin(4 * PI * hz * t) + 0.15 * sin(6 * PI * hz * t) + noise * (r.nextDouble() - 0.5)).toFloat()
    }
  }

  @Test
  fun yinFindsTheFundamentalAcrossTheHornRange() {
    for (hz in listOf(65.4, 116.5, 233.1, 440.0, 622.3, 1046.5)) {
      val found = Yin.detect(tone(hz, noise = 0.05), 44_100)!!
      assertTrue("$hz heard as $found", abs(1200 * kotlin.math.log2(found / hz)) < 3)
    }
  }

  @Test
  fun yinHearsNothingInSilenceOrNoise() {
    assertNull(Yin.detect(FloatArray(4_096), 44_100))
    val r = Random(2)
    assertNull(Yin.detect(FloatArray(4_096) { (r.nextDouble() - 0.5).toFloat() }, 44_100))
  }

  @Test
  fun notesAreNamedWithCentsAndWrittenForTheHorn() {
    val a = NoteReading.of(440.0)
    assertEquals("A", a.name)
    assertEquals(4, a.octave)
    assertEquals(0, a.cents)
    assertEquals(23, NoteReading.of(446.0).cents)
    assertEquals(-34, NoteReading.of(431.4).cents)
    assertEquals(5, NoteReading.of(441.3).cents)
    val concertC = NoteReading.of(261.63)
    assertEquals("D" to 4, concertC.writtenFor(Transposition.Bb).let { it.name to it.octave })
    assertEquals("A" to 4, concertC.writtenFor(Transposition.Eb).let { it.name to it.octave })
    assertEquals(0, NoteReading.of(432.0, a4 = 432.0).cents)
  }
}
