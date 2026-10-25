package com.larakelley.woodshed.audio

import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.core.content.edit
import com.larakelley.woodshed.domain.ChordQuality
import com.larakelley.woodshed.domain.DroneRange
import com.larakelley.woodshed.domain.DroneTone
import com.larakelley.woodshed.domain.Voicing
import com.larakelley.woodshed.domain.voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DroneState(
  val playing: Boolean = false,
  /** Concert pitch class, C = 0. */
  val pitchClass: Int = 0,
  val range: DroneRange = DroneRange.MIDDLE,
  val chord: ChordQuality = ChordQuality.NOTE,
  val voicing: Voicing = Voicing.CLOSE,
  val volume: Float = 0.6f,
)

/** Plays a [DroneTone] from its own thread. Settings apply while it plays and are kept between launches. */
class Drone(private val prefs: SharedPreferences, private val a4: () -> Int) {
  private val _state = MutableStateFlow(
    DroneState(
      pitchClass = prefs.getInt(PITCH, 0).coerceIn(0, 11),
      range = DroneRange.entries.firstOrNull { it.name == prefs.getString(RANGE, null) } ?: DroneRange.MIDDLE,
      chord = ChordQuality.entries.firstOrNull { it.name == prefs.getString(CHORD, null) } ?: ChordQuality.NOTE,
      voicing = Voicing.entries.firstOrNull { it.name == prefs.getString(VOICING, null) } ?: Voicing.CLOSE,
      volume = prefs.getFloat(VOLUME, 0.6f).coerceIn(0f, 1f),
    ),
  )
  val state: StateFlow<DroneState> = _state.asStateFlow()
  private var thread: Thread? = null

  /** Runs as it starts, so whatever else is sounding can stop; one thing sounds at a time. */
  var onStart: () -> Unit = {}

  fun toggle() = if (_state.value.playing) stop() else start()

  fun setPitchClass(pc: Int) = update { it.copy(pitchClass = Math.floorMod(pc, 12)) }

  fun setRange(range: DroneRange) = update { it.copy(range = range) }

  fun setChord(chord: ChordQuality) = update { it.copy(chord = chord) }

  fun setVoicing(voicing: Voicing) = update { it.copy(voicing = voicing) }

  fun setVolume(volume: Float) = update { it.copy(volume = volume.coerceIn(0f, 1f)) }

  fun start() {
    // Whatever else is sounding stops first, outside this lock, so tools starting together never wait on each other.
    onStart()
    startThread()
  }

  @Synchronized
  private fun startThread() {
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
      putString(RANGE, next.range.name)
      putString(CHORD, next.chord.name)
      putString(VOICING, next.voicing.name)
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
        val reached = tone.render(buffer, s.range.rootHz(s.pitchClass, a4().toDouble()), if (stopping) 0.0 else s.volume.toDouble(), voice(s.chord, s.voicing))
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

    const val PITCH = "drone_pitch"
    const val RANGE = "drone_range"
    const val CHORD = "drone_chord"
    const val VOICING = "drone_voicing"
    const val VOLUME = "drone_volume"
  }
}
