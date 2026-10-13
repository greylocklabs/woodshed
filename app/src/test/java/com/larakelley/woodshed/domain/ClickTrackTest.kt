package com.larakelley.woodshed.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong

class ClickTrackTest {
  private val rate = 48_000

  /** Frames where a click starts: the first non-zero sample after a stretch of silence longer than a click. */
  private fun onsets(track: ClickTrack, frames: Int, chunk: Int): List<Long> {
    val out = ShortArray(chunk)
    val found = mutableListOf<Long>()
    var lastSound = -10_000L
    var frame = 0L
    while (frame < frames) {
      track.render(frame, out)
      for (i in out.indices) {
        if (out[i] != 0.toShort()) {
          if (frame + i - lastSound > 5_000) found += frame + i
          lastSound = frame + i
        }
      }
      frame += chunk
    }
    return found
  }

  @Test
  fun clicksLandOnExactFrames() {
    // At 48 kHz and 80 bpm a beat is exactly 36,000 frames.
    val track = ClickTrack(rate, 80, 4)
    assertEquals(listOf(1L, 36_001L, 72_001L, 108_001L), onsets(track, 140_000, 960).map { it })
    assertEquals(36_000L, track.beatFrame(1))
  }

  @Test
  fun tenMinutesLaterTheGridHasNotDrifted() {
    val bpm = 97 // 29,690.72 frames per beat: rounding error must not accumulate
    val track = ClickTrack(rate, bpm, 4)
    val beats = 10L * bpm
    assertEquals((beats * rate * 60.0 / bpm).roundToLong(), track.beatFrame(beats))
    assertEquals(28_800_000L, track.beatFrame(beats))
  }

  @Test
  fun chunkSizeDoesNotChangeTheOutput() {
    val track = ClickTrack(rate, 133, 3)
    val whole = ShortArray(200_000).also { track.render(0, it) }
    val pieces = ShortArray(200_000)
    var frame = 0
    val chunk = ShortArray(777)
    while (frame < pieces.size) {
      track.render(frame.toLong(), chunk)
      chunk.copyInto(pieces, frame, 0, minOf(chunk.size, pieces.size - frame))
      frame += chunk.size
    }
    assertArrayEquals(whole, pieces)
  }

  @Test
  fun firstBeatOfEachBarIsAccented() {
    val track = ClickTrack(rate, 120, 3)
    assertEquals(listOf(true, false, false, true, false, false), (0L..5L).map(track::isAccent))
    val accent = ShortArray(400).also { track.render(track.beatFrame(3), it) }
    val plain = ShortArray(400).also { track.render(track.beatFrame(4), it) }
    assertTrue(!accent.contentEquals(plain))
  }

  @Test
  fun silenceBetweenClicks() {
    val track = ClickTrack(rate, 60, 4)
    val gap = ShortArray(10_000).also { track.render(20_000, it) }
    assertTrue(gap.all { it == 0.toShort() })
  }

  @Test
  fun beatsPerBarFromTimeSignature() {
    assertEquals(3, ClickTrack.beatsPerBar("3/4"))
    assertEquals(5, ClickTrack.beatsPerBar("5/4"))
    assertEquals(4, ClickTrack.beatsPerBar(null))
    assertEquals(4, ClickTrack.beatsPerBar("cut"))
  }
}
