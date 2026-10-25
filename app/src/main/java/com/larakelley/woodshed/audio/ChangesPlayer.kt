package com.larakelley.woodshed.audio

import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.core.content.edit
import com.larakelley.woodshed.domain.Bar
import com.larakelley.woodshed.domain.ChangesBeat
import com.larakelley.woodshed.domain.ChangesTimeline
import com.larakelley.woodshed.domain.ClickTrack
import com.larakelley.woodshed.domain.DroneTone
import com.larakelley.woodshed.domain.Feel
import com.larakelley.woodshed.domain.voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil
import kotlin.math.floor

/** A medium swing, a common place to start working on changes. */
private const val DEFAULT_BPM = 120

/**
 * Something to play: its name, its bars relative to the key for each time through, and [keyId], what its key is
 * remembered under, which stays the same when [id] changes with an edit or a pattern's settings.
 */
data class ChangesSource(val id: String, val keyId: String, val title: String, val bars: (chorus: Int) -> List<Bar>)

data class ChangesState(
  val playing: Boolean = false,
  /** Concert pitch class the loaded changes are played in. */
  val key: Int = 0,
  /** The key each form, pattern and set of the user's changes was last played in, by [ChangesSource.keyId]. */
  val keys: Map<String, Int> = emptyMap(),
  /** The changes keep a tempo of their own, apart from the metronome's, which practice blocks set. */
  val bpm: Int = DEFAULT_BPM,
  val click: Boolean = true,
  val countIn: Boolean = true,
  val keepGoing: Boolean = true,
  /** The id of what is loaded, and its name. */
  val source: String? = null,
  val title: String = "",
  /** The bars of the time through being heard, relative to [key]. */
  val bars: List<Bar> = emptyList(),
  val chorus: Int = 0,
  /** The bar and beat being heard, from 0; -1 when stopped or counting in. */
  val bar: Int = -1,
  val beat: Int = -1,
  /** The count-in beat being heard, from 0, or -1. */
  val countInBeat: Int = -1,
) {
  /** The concert key [keyId] plays in: its own, or else the one played last. */
  fun keyFor(keyId: String): Int = keys[keyId] ?: key
}

/**
 * Plays changes: the drone's sound moving from chord to chord in time, over the metronome's clicks, from one audio
 * thread so chords land on the beat. Beats per bar, accent and feel are the metronome's, and range, voicing and
 * volume the drone's, so each tool keeps the settings the others use; the tempo is the changes' own.
 */
class ChangesPlayer(private val prefs: SharedPreferences, private val metronome: Metronome, private val drone: Drone, private val a4: () -> Int) {
  private val _state = MutableStateFlow(
    ChangesState(
      key = prefs.getInt(KEY, 0).coerceIn(0, 11),
      keys = prefs.all.mapNotNull { (name, value) -> if (name.startsWith(KEY_OF) && value is Int) name.removePrefix(KEY_OF) to value else null }.toMap(),
      bpm = prefs.getInt(BPM, DEFAULT_BPM).coerceIn(ClickTrack.MIN_BPM, ClickTrack.MAX_BPM),
      click = prefs.getBoolean(CLICK, true),
      countIn = prefs.getBoolean(COUNT_IN, true),
      keepGoing = prefs.getBoolean(KEEP_GOING, true),
    ),
  )
  val state: StateFlow<ChangesState> = _state.asStateFlow()

  @Volatile private var source: ChangesSource? = null
  private var keyId: String? = null
  private var thread: Thread? = null

  fun toggle() = if (_state.value.playing) stop() else start()

  /** Loads [next] to play, stopping whatever else was playing. */
  fun load(next: ChangesSource) {
    if (_state.value.source == next.id) return
    stop()
    source = next
    keyId = next.keyId
    _state.value = _state.value.copy(source = next.id, title = next.title, key = _state.value.keyFor(next.keyId), bars = next.bars(0), chorus = 0)
  }

  /** Sets the key [keyId] plays in; when it is what is loaded, it starts over in the new key. */
  fun setKey(keyId: String, key: Int) {
    val pitch = Math.floorMod(key, 12)
    prefs.edit { putInt(KEY_OF + keyId, pitch) }
    val loaded = keyId == this.keyId
    update(restart = loaded) { it.copy(keys = it.keys + (keyId to pitch), key = if (loaded) pitch else it.key) }
  }

  /** Starts over from the count-in, as a change to beats per bar needs, if it is playing. */
  fun restart() {
    if (!_state.value.playing) return
    stop()
    start()
  }

  fun setBpm(bpm: Int) = update { it.copy(bpm = bpm.coerceIn(ClickTrack.MIN_BPM, ClickTrack.MAX_BPM)) }

  fun setClick(click: Boolean) = update { it.copy(click = click) }

  fun setCountIn(countIn: Boolean) = update { it.copy(countIn = countIn) }

  fun setKeepGoing(keepGoing: Boolean) = update { it.copy(keepGoing = keepGoing) }

  @Synchronized
  fun start() {
    if (thread != null || source == null) return
    // One thing sounds at a time; the changes bring their own click and drone.
    metronome.stop()
    drone.stop()
    _state.value = _state.value.copy(playing = true)
    thread = Thread(::run, "changes").apply {
      priority = Thread.MAX_PRIORITY
      start()
    }
  }

  /** Fades the chord out rather than cutting it off. */
  @Synchronized
  fun stop() {
    _state.value = _state.value.copy(playing = false, bar = -1, beat = -1, countInBeat = -1)
    thread = null
  }

  private fun update(restart: Boolean = false, change: (ChangesState) -> ChangesState) {
    val next = change(_state.value)
    prefs.edit {
      putInt(KEY, next.key)
      putInt(BPM, next.bpm)
      putBoolean(CLICK, next.click)
      putBoolean(COUNT_IN, next.countIn)
      putBoolean(KEEP_GOING, next.keepGoing)
    }
    _state.value = next
    if (restart) restart()
  }

  private fun run() {
    val me = Thread.currentThread()
    val playing = source ?: return
    val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val audio = AudioTrack.Builder()
      .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
      .setAudioFormat(
        AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
      )
      .setBufferSizeInBytes(minBuffer * 2)
      .setTransferMode(AudioTrack.MODE_STREAM)
      .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
      .build()
    val countIn = _state.value.countIn
    val beats = metronome.state.value.beatsPerBar
    val timeline = ChangesTimeline(beats, if (countIn) beats else 0, { _state.value.keepGoing }, playing.bars)
    val tone = DroneTone(SAMPLE_RATE)
    val buffer = ShortArray(CHUNK_FRAMES)
    val segment = ShortArray(CHUNK_FRAMES)
    // The position in beats, counted from the first count-in beat. Tempo changes alter how fast it moves, never
    // where it is, so a new tempo carries on from the beat being played.
    var position = 0.0
    var lastBeat = -1L
    var click: ShortArray? = null
    var clickAt = 0
    var written = 0L
    var finished = false
    var clicks: ClickTrack? = null
    var countClicks: ClickTrack? = null
    try {
      audio.play()
      while (true) {
        val m = metronome.state.value
        val d = drone.state.value
        val stopping = !_state.value.playing || thread !== me || finished
        val bpm = _state.value.bpm
        val framesPerBeat = SAMPLE_RATE * 60.0 / bpm
        val beatClicks = clicks?.takeIf { it.bpm == bpm && it.accentFirst == m.accentFirst && it.feel == m.feel }
          ?: ClickTrack(SAMPLE_RATE, bpm, beats, m.accentFirst, m.feel).also { clicks = it }
        // The count-in clicks every beat, whatever the feel, so the first bar is easy to find.
        val counting =
          countClicks?.takeIf { it.bpm == bpm } ?: ClickTrack(SAMPLE_RATE, bpm, beats, accentFirst = true, Feel.EVERY_BEAT).also { countClicks = it }
        var filled = 0
        var reached = 0.0
        while (filled < buffer.size) {
          val index = floor(position).toLong()
          val at = timeline.at(index)
          if (index != lastBeat && !stopping) {
            lastBeat = index
            click = when (at) {
              is ChangesBeat.CountIn -> counting.clickFor(at.beat.toLong())
              is ChangesBeat.Playing -> if (_state.value.click) beatClicks.clickFor(at.beat.toLong()) else null
              ChangesBeat.Done -> null
            }
            clickAt = 0
          }
          if (at == ChangesBeat.Done) finished = true
          val untilNextBeat = ceil((index + 1 - position) * framesPerBeat).toInt().coerceAtLeast(1)
          val frames = minOf(untilNextBeat, buffer.size - filled)
          val chord = (at as? ChangesBeat.Playing)?.chord
          val out = if (frames == segment.size) segment else ShortArray(frames)
          reached = if (chord == null) {
            tone.render(out, 110.0, 0.0)
          } else {
            val root = d.range.rootHz(Math.floorMod(_state.value.key + chord.root, 12), a4().toDouble())
            tone.render(out, root, if (stopping) 0.0 else d.volume.toDouble(), voice(chord.quality, d.voicing))
          }
          for (i in 0 until frames) {
            var sample = out[i].toInt()
            val wave = click
            if (wave != null && clickAt < wave.size) sample += wave[clickAt++]
            buffer[filled + i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
          }
          filled += frames
          position += frames / framesPerBeat
        }
        if (audio.write(buffer, 0, buffer.size) < 0) break
        written += buffer.size
        if (stopping && reached <= 0.0001) break
        if (!stopping) report(timeline, position - (written - audio.playbackHeadPosition.toLong()) / framesPerBeat)
      }
    } finally {
      audio.pause()
      audio.flush()
      audio.release()
      // Played through once: the screen goes back to ready.
      if (finished && thread === me) stop()
    }
  }

  /** Shows the beat being heard, which trails what was written by what the track still holds. */
  private fun report(timeline: ChangesTimeline, heard: Double) {
    if (heard < 0) return
    val s = _state.value
    val next = when (val at = timeline.at(floor(heard).toLong())) {
      is ChangesBeat.CountIn -> s.copy(countInBeat = at.beat, bar = -1, beat = -1)
      is ChangesBeat.Playing -> s.copy(countInBeat = -1, bar = at.bar, beat = at.beat, chorus = at.chorus, bars = timeline.bars(at.chorus))
      ChangesBeat.Done -> s
    }
    if (next != s && s.playing) _state.value = next
  }

  private companion object {
    const val SAMPLE_RATE = 48_000
    const val CHUNK_FRAMES = 960
    const val KEY = "changes_key"

    /** Each item's own key is kept under this prefix and its key id. */
    const val KEY_OF = "changes_key:"
    const val BPM = "changes_bpm"
    const val CLICK = "changes_click"
    const val COUNT_IN = "changes_count_in"
    const val KEEP_GOING = "changes_keep_going"
  }
}
