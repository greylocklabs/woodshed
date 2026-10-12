package com.larakelley.woodshed.audio

import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.core.content.edit
import com.larakelley.woodshed.domain.ClickTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MetronomeState(val running: Boolean = false, val bpm: Int = 100, val beatsPerBar: Int = 4)

/**
 * Streams a [ClickTrack] to an AudioTrack from a dedicated thread. Clicks are placed in the sample stream rather
 * than triggered by timers, which jitter audibly.
 */
class Metronome(private val prefs: SharedPreferences) {
  private val _state = MutableStateFlow(MetronomeState(bpm = prefs.getInt(BPM, 100).coerceIn(ClickTrack.MIN_BPM, ClickTrack.MAX_BPM)))
  val state: StateFlow<MetronomeState> = _state.asStateFlow()

  @Volatile private var track: ClickTrack? = null
  @Volatile private var restartAt = 0L
  private var thread: Thread? = null

  fun toggle() = if (_state.value.running) stop() else start()

  fun setBpm(bpm: Int) {
    val clamped = bpm.coerceIn(ClickTrack.MIN_BPM, ClickTrack.MAX_BPM)
    prefs.edit { putInt(BPM, clamped) }
    _state.value = _state.value.copy(bpm = clamped)
    if (_state.value.running) retune()
  }

  fun setBeatsPerBar(beats: Int) {
    if (beats == _state.value.beatsPerBar) return
    _state.value = _state.value.copy(beatsPerBar = beats)
    if (_state.value.running) retune()
  }

  @Synchronized
  fun start() {
    if (thread != null) return
    _state.value = _state.value.copy(running = true)
    retune()
    thread = Thread(::run, "metronome").apply {
      priority = Thread.MAX_PRIORITY
      start()
    }
  }

  @Synchronized
  fun stop() {
    _state.value = _state.value.copy(running = false)
    thread?.interrupt()
    thread = null
  }

  /** A tempo change restarts the beat grid at the next buffer, so the change lands immediately. */
  private fun retune() {
    val s = _state.value
    track = ClickTrack(SAMPLE_RATE, s.bpm, s.beatsPerBar)
    restartAt = -1
  }

  private fun run() {
    val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val audio = AudioTrack.Builder()
      .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
      .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
      .setBufferSizeInBytes(minBuffer * 2)
      .setTransferMode(AudioTrack.MODE_STREAM)
      .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
      .build()
    val buffer = ShortArray(CHUNK_FRAMES)
    var written = 0L
    var origin = 0L
    try {
      audio.play()
      while (!Thread.currentThread().isInterrupted) {
        if (restartAt < 0) {
          origin = written
          restartAt = written
        }
        val current = track ?: break
        current.render(written - origin, buffer)
        if (audio.write(buffer, 0, buffer.size) < 0) break
        written += buffer.size
      }
    } finally {
      audio.pause()
      audio.flush()
      audio.release()
    }
  }

  private companion object {
    const val SAMPLE_RATE = 48_000
    const val CHUNK_FRAMES = 960
    const val BPM = "metronome_bpm"
  }
}
