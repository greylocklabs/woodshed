package com.larakelley.woodshed.session

import android.content.SharedPreferences
import androidx.core.content.edit
import com.larakelley.woodshed.data.Card
import com.larakelley.woodshed.data.PracticeService
import com.larakelley.woodshed.data.SessionLog
import com.larakelley.woodshed.data.TranscriptionService
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.data.db.BlockOutcome
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.MediaLink
import com.larakelley.woodshed.domain.PlanBlock
import com.larakelley.woodshed.domain.delayLabel
import com.larakelley.woodshed.ui.practice.StepView
import com.larakelley.woodshed.ui.practice.cardView
import com.larakelley.woodshed.ui.practice.droneNote
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A session in progress. Not running means none; running with no step means the last block is done. */
data class SessionState(
  val running: Boolean = false,
  val step: StepView? = null,
  val stepIndex: Int = 0,
  val stepCount: Int = 0,
  /** When the current block began, in epoch milliseconds. Resuming from a pause moves it forward by the pause. */
  val stepStartedAt: Long = 0,
  /** When the session was paused, or null while it runs. */
  val pausedAt: Long? = null,
  val reviewed: Int = 0,
) {
  /** When the current block's planned time runs out. */
  val stepEndsAt: Long?
    get() = step?.let { stepStartedAt + it.minutes * 60_000L }
}

/** What survives the process being killed: the plan, where the user is in it, and its clock. */
@Serializable
data class SavedSession(
  val blocks: List<PlanBlock>,
  val index: Int,
  val stepStartedAt: Long,
  val pausedAt: Long? = null,
  val reviewed: Int = 0,
  /** The session's row in the history. */
  val sessionId: Long,
  /** True when the session already passed its last block, and the user has gone back to one since. */
  val completed: Boolean = false,
)

/** Keeps the running session in its own preferences file, outside the settings that backups carry. */
class SessionStore(private val prefs: SharedPreferences) {
  fun save(session: SavedSession?) = prefs.edit { if (session == null) remove(KEY) else putString(KEY, Json.encodeToString(session)) }

  fun load(): SavedSession? = prefs.getString(KEY, null)?.let { runCatching { Json.decodeFromString<SavedSession>(it) }.getOrNull() }

  private companion object {
    const val KEY = "running_session"
  }
}

/**
 * Runs a practice session for the whole app rather than one screen, so the notification, the session screen
 * and a restart after the process is killed all see the same session. Calls are applied one at a time, in order.
 */
class SessionRunner(
  private val repository: WoodshedRepository,
  private val practice: PracticeService,
  private val transcriptions: TranscriptionService?,
  private val store: SessionStore,
  private val log: SessionLog,
  private val scope: CoroutineScope,
  private val now: () -> Long = System::currentTimeMillis,
) {
  private val _state = MutableStateFlow(SessionState())
  val state: StateFlow<SessionState> = _state.asStateFlow()

  /** Completes once [restore] has looked for a saved session, so nothing mistakes "not restored yet" for "none". */
  val ready = CompletableDeferred<Unit>()

  private val lock = Mutex()
  private var blocks: List<PlanBlock> = emptyList()
  private var card: Card? = null
  private var solo: Long? = null
  private var sessionId: Long? = null

  /** True once the session has passed its last block; going back to a block afterwards does not undo it. */
  private var completed = false

  /** Picks up a saved session where it left off, unless it is more than [STALE_MS] old. */
  fun restore() = serially {
    val saved = store.load()
    if (saved != null && now() - saved.stepStartedAt < STALE_MS && saved.index in saved.blocks.indices) {
      blocks = saved.blocks
      sessionId = saved.sessionId
      completed = saved.completed
      _state.value = SessionState(running = true, stepCount = blocks.size, reviewed = saved.reviewed)
      show(saved.index, startedAt = saved.stepStartedAt, pausedAt = saved.pausedAt)
    } else if (saved != null) {
      store.save(null)
    }
    ready.complete(Unit)
  }

  fun start(plan: List<PlanBlock>) = serially {
    if (plan.isEmpty()) return@serially
    blocks = plan
    card = null
    solo = null
    sessionId = log.start(plan, repository.instrument.value?.name, now())
    completed = false
    _state.value = SessionState(running = true, stepCount = plan.size)
    show(0)
  }

  /** Finishes an exercise or changes block, or moves past a tune or solo block without grading it. */
  fun next() = serially {
    if (!_state.value.running || _state.value.step == null) return@serially
    record(if (_state.value.step is StepView.Exercise || _state.value.step is StepView.Changes) BlockOutcome.DONE else BlockOutcome.SKIPPED, null)
    card = null
    solo = null
    show(_state.value.stepIndex + 1)
  }

  /** Goes back to the block before this one, or from the end to the last block, skipping any that have gone. */
  fun previous() = serially {
    val s = _state.value
    if (!s.running) return@serially
    for (index in s.stepIndex - 1 downTo 0) {
      card = null
      solo = null
      val step = view(blocks[index]) ?: continue
      _state.value = s.copy(step = step, stepIndex = index, stepStartedAt = now(), pausedAt = null)
      save()
      return@serially
    }
  }

  /** Grades the current tune or solo block and moves on. A grade with nothing to grade, such as a second tap, does nothing. */
  fun grade(grade: Grade) = serially {
    val soloId = solo
    val tuneCard = card
    when {
      soloId != null -> transcriptions?.grade(soloId, grade)
      tuneCard != null -> practice.grade(tuneCard, grade)
      else -> return@serially
    }
    record(BlockOutcome.GRADED, grade)
    card = null
    solo = null
    _state.value = _state.value.copy(reviewed = _state.value.reviewed + 1)
    show(_state.value.stepIndex + 1)
  }

  fun pause() = serially {
    val s = _state.value
    if (s.step == null || s.pausedAt != null) return@serially
    _state.value = s.copy(pausedAt = now())
    save()
  }

  fun resume() = serially {
    val s = _state.value
    val pausedAt = s.pausedAt ?: return@serially
    _state.value = s.copy(stepStartedAt = s.stepStartedAt + (now() - pausedAt), pausedAt = null)
    save()
  }

  fun stop() = serially {
    // A session worked to the end was logged as completed when its last block finished; ending one early logs it here.
    if (_state.value.running && !completed) sessionId?.let { log.end(it, now(), completed = false) }
    sessionId = null
    completed = false
    blocks = emptyList()
    card = null
    solo = null
    _state.value = SessionState()
    store.save(null)
  }

  /** Logs how the current block ended, with the time spent on it less any pause. */
  private suspend fun record(outcome: BlockOutcome, grade: Grade?) {
    val id = sessionId ?: return
    val s = _state.value
    val at = now()
    log.finish(id, s.stepIndex, outcome, grade, ((s.pausedAt ?: at) - s.stepStartedAt).coerceAtLeast(0), at)
  }

  private fun serially(action: suspend () -> Unit) = scope.launch { lock.withLock { action() } }

  private fun save() {
    val s = _state.value
    val id = sessionId
    store.save(
      if (s.running && s.step != null && id != null) SavedSession(blocks, s.stepIndex, s.stepStartedAt, s.pausedAt, s.reviewed, id, completed) else null,
    )
  }

  /** Shows block [index], skipping any whose tune, exercise or solo has gone; past the end, the session is done. */
  private suspend fun show(index: Int, startedAt: Long = now(), pausedAt: Long? = null) {
    val block = blocks.getOrNull(index)
    val step = block?.let { view(it) }
    if (block != null && step == null) return show(index + 1, startedAt, pausedAt)
    _state.value = _state.value.copy(step = step, stepIndex = index, stepStartedAt = startedAt, pausedAt = pausedAt)
    save()
    // Past the last block the session is complete, whether or not Finish is ever tapped.
    if (block == null && !completed) {
      completed = true
      sessionId?.let { log.end(it, now(), completed = true) }
    }
  }

  private suspend fun view(block: PlanBlock): StepView? = when (block) {
    is PlanBlock.Exercise ->
      repository.exercises.first().firstOrNull {
        it.id == block.exerciseId
      }?.let { e ->
        val drone = block.dronePitchClass?.let { droneNote(it, e.category, repository.transposition.value) }
        StepView.Exercise(e.name, e.category, e.description, e.tempoBpm, block.minutes, drone)
      }

    is PlanBlock.Tune -> practice.cardForBlock(block, repository.transposition.value)?.let { c ->
      card = c
      val preview = practice.preview(c).mapValues { it.value.dueAt ?: now() }
      StepView.Tune(cardView(c, preview, now()), block.minutes)
    }

    is PlanBlock.Solo -> repository.transcriptions.first().firstOrNull { it.id == block.transcriptionId }?.let { s ->
      solo = s.id
      val notes = repository.transcriptionStates.first().firstOrNull { it.transcriptionId == s.id }?.notes
      val pad = TranscriptionService.PADDING_SECONDS
      val embed = MediaLink.parse(s.url)?.embed(s.startSeconds?.let { (it - pad).coerceAtLeast(0) }, s.endSeconds?.let { it + pad })
      val preview = transcriptions?.preview(s.id).orEmpty()
      StepView.Solo(
        s.title,
        "${s.soloist}, ${s.instrument.lowercase()}",
        notes,
        embed,
        s.url,
        s.startSeconds,
        Grade.entries.map { g -> g to delayLabel(now(), preview[g] ?: now()) },
        block.minutes,
      )
    }

    is PlanBlock.Changes -> {
      val written = Math.floorMod(block.concertKey + repository.transposition.value.offset, 12)
      StepView.Changes(block.title, Key.MAJOR_SPELLING[written], block.route, block.concertKey, block.minutes)
    }
  }

  companion object {
    /** A session left this long is over, not interrupted. */
    const val STALE_MS = 12 * 60 * 60 * 1000L
  }
}
