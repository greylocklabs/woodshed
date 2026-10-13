package com.larakelley.woodshed.audio

import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.core.content.edit
import com.larakelley.woodshed.domain.DroneTone
import com.larakelley.woodshed.domain.pitchHz
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DroneState(
  val playing: Boolean = false,
  /** Concert pitch class, C = 0. */
  val pitchClass: Int = 0,
  val octave: Int = 4,
  val fifth: Boolean = false,
  val volume: Float = 0.6f,
)

/** Plays a [DroneTone] from its own thread. Settings apply while it plays and are kept between launches. */
class Drone(private val prefs: SharedPreferences, private val a4: () -> Int) {
  private val _state = MutableStateFlow(
    DroneState(
      pitchClass = prefs.getInt(PITCH, 0).coerceIn(0, 11),
      octave = prefs.getInt(OCTAVE, 4).coerceIn(OCTAVES),
      fifth = prefs.getBoolean(FIFTH, false),
      volume = prefs.getFloat(VOLUME, 0.6f).coerceIn(0f, 1f),
    ),
  )
  val state: StateFlow<DroneState> = _state.asStateFlow()
  private var thread: Thread? = null

  fun toggle() = if (_state.value.playing) stop() else start()

  fun setPitchClass(pc: Int) = update { it.copy(pitchClass = Math.floorMod(pc, 12)) }

  fun setOctave(octave: Int) = update { it.copy(octave = octave.coerceIn(OCTAVES)) }

  fun setFifth(fifth: Boolean) = update { it.copy(fifth = fifth) }

  fun setVolume(volume: Float) = update { it.copy(volume = volume.coerceIn(0f, 1f)) }

  @Synchronized
  fun start() {
    if (thread != null) return
    _state.value = _state.value.copy(playing = true)
    thread = Thread(::run, "drone").apply { start() }
  }

  /** Fades out over a moment rather than cutting off. */
  @Synchronized
  fun stop() {
    _state.value = _state.value.copy(playing = false)
    thread = null
  }

  private fun update(change: (DroneState) -> DroneState) {
    val next = change(_state.value)
    prefs.edit {
      putInt(PITCH, next.pitchClass)
      putInt(OCTAVE, next.octave)
      putBoolean(FIFTH, next.fifth)
      putFloat(VOLUME, next.volume)
    }
    _state.value = next
  }

  private fun run() {
    val me = Thread.currentThread()
    val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val audio = AudioTrack.Builder()
      .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
      .setAudioFormat(
        AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
      )
      .setBufferSizeInBytes(minBuffer * 4)
      .setTransferMode(AudioTrack.MODE_STREAM)
      .build()
    val tone = DroneTone(SAMPLE_RATE)
    val buffer = ShortArray(CHUNK_FRAMES)
    try {
      audio.play()
      while (true) {
        val s = _state.value
        // This thread is done once stopped or replaced; it keeps rendering at zero volume until faded out.
        val stopping = !s.playing || thread !== me
        val reached = tone.render(buffer, pitchHz(s.pitchClass, s.octave, a4().toDouble()), if (stopping) 0.0 else s.volume.toDouble(), s.fifth)
        if (audio.write(buffer, 0, buffer.size) < 0) break
        if (stopping && reached <= 0.0001) break
      }
    } finally {
      audio.pause()
      audio.flush()
      audio.release()
    }
  }

  private companion object {
    const val SAMPLE_RATE = 48_000

    /** Each buffer is also one fade step, about 40 ms. */
    const val CHUNK_FRAMES = 2_048

    /** Phone speakers lose most of a note below about 150 Hz, so the middle range starts at C4 rather than C3. */
    val OCTAVES = 3..5
    const val PITCH = "drone_pitch"
    const val OCTAVE = "drone_octave"
    const val FIFTH = "drone_fifth"
    const val VOLUME = "drone_volume"
  }
}
