package com.larakelley.woodshed.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.content.SharedPreferences
import androidx.core.content.edit
import com.larakelley.woodshed.domain.NoteReading
import com.larakelley.woodshed.domain.Yin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TunerState(
  val listening: Boolean = false,
  /** The last clear pitch heard, or null before one. */
  val reading: NoteReading? = null,
  val a4: Int = 440,
  /** Set when the microphone could not be opened or stopped delivering sound. */
  val failed: Boolean = false,
)

/** Listens on the microphone from its own thread and names the pitch it hears. */
class Tuner(private val prefs: SharedPreferences) {
  private val _state = MutableStateFlow(TunerState(a4 = prefs.getInt(A4, 440).coerceIn(A4_RANGE)))
  val state: StateFlow<TunerState> = _state.asStateFlow()
  private var thread: Thread? = null

  fun setA4(hz: Int) {
    val clamped = hz.coerceIn(A4_RANGE)
    prefs.edit { putInt(A4, clamped) }
    _state.value = _state.value.copy(a4 = clamped)
  }

  /** Callers must hold the RECORD_AUDIO permission. */
  @Synchronized
  fun start() {
    if (thread != null) return
    _state.value = _state.value.copy(listening = true, failed = false)
    thread = Thread(::run, "tuner").apply { start() }
  }

  @Synchronized
  fun stop() {
    _state.value = _state.value.copy(listening = false)
    thread?.interrupt()
    thread = null
  }

  /** Ends listening from the tuner thread itself, when the microphone fails rather than the user stopping it. */
  @Synchronized
  private fun fail(from: Thread) {
    if (thread !== from) return
    thread = null
    _state.value = _state.value.copy(listening = false, failed = true)
  }

  @SuppressLint("MissingPermission")
  private fun run() {
    val me = Thread.currentThread()
    val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
    val record = runCatching {
      AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, maxOf(minBuffer, FRAME * 4))
    }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
    if (record == null) return fail(me)
    val frame = FloatArray(FRAME)
    var smoothed: Double? = null
    try {
      record.startRecording()
      while (!me.isInterrupted) {
        var read = 0
        while (read < FRAME) {
          val n = record.read(frame, read, FRAME - read, AudioRecord.READ_BLOCKING)
          if (n < 0) return fail(me)
          read += n
        }
        val hz = Yin.detect(frame, SAMPLE_RATE) ?: continue
        // Light smoothing steadies the needle; a jump of more than a semitone is a new note and resets it.
        val prev = smoothed
        smoothed = if (prev == null || kotlin.math.abs(12 * kotlin.math.log2(hz / prev)) > 1) hz else prev * 0.6 + hz * 0.4
        _state.value = _state.value.copy(reading = NoteReading.of(smoothed!!, _state.value.a4.toDouble()))
      }
    } catch (_: IllegalStateException) {
      fail(me)
    } finally {
      runCatching { record.stop() }
      record.release()
    }
  }

  private companion object {
    const val SAMPLE_RATE = 44_100
    const val FRAME = 4_096
    const val A4 = "tuner_a4"
    val A4_RANGE = 415..466
  }
}
