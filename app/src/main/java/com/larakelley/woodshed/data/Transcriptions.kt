package com.larakelley.woodshed.data

import androidx.room.withTransaction
import com.larakelley.woodshed.data.db.Performer
import com.larakelley.woodshed.data.db.ReviewEventEntity
import com.larakelley.woodshed.data.db.TranscriptionEntity
import com.larakelley.woodshed.data.db.TranscriptionStateEntity
import com.larakelley.woodshed.data.db.TranscriptionStatus
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.seed.SeedValidator
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.MediaLink
import com.larakelley.woodshed.domain.Scheduler
import com.larakelley.woodshed.domain.Sm2Scheduler
import com.larakelley.woodshed.domain.Tonality

/** A solo as the editor fills it in. Times are "m:ss" or seconds; blank means unknown. */
data class TranscriptionDraft(
  val title: String = "",
  val soloist: String = "",
  val instrument: String = "",
  val context: String = "",
  val url: String = "",
  val start: String = "",
  val end: String = "",
  val keyName: String? = null,
  val tonality: Tonality? = Tonality.MAJOR,
  /** One "Name - Instrument" per line. */
  val performers: String = "",
  val tuneId: Long? = null,
) {
  fun problems(): List<String> = buildList {
    if (title.isBlank()) add("A tune title is needed.")
    if (soloist.isBlank()) add("Who plays the solo?")
    if (instrument.isBlank()) add("What do they play?")
    if (MediaLink.parse(url) == null) add("The recording needs a web address.")
    if (start.isNotBlank() && parseClock(start) == null) add("Start should look like 2:00.")
    if (end.isNotBlank() && parseClock(end) == null) add("End should look like 5:29.")
    val s = parseClock(start)
    val e = parseClock(end)
    if (s != null && e != null && e <= s) add("The solo should end after it starts.")
  }

  fun performerList(): List<Performer> = performers.lines().mapNotNull { line ->
    val name = line.substringBefore(" - ").trim()
    val instrument = line.substringAfter(" - ", "").trim()
    if (name.isEmpty()) null else Performer(name, instrument)
  }

  companion object {
    fun of(t: TranscriptionEntity) = TranscriptionDraft(
      title = t.title,
      soloist = t.soloist,
      instrument = t.instrument,
      context = t.context.orEmpty(),
      url = t.url,
      start = t.startSeconds?.let(::clock).orEmpty(),
      end = t.endSeconds?.let(::clock).orEmpty(),
      keyName = t.keyName,
      tonality = t.tonality,
      performers = t.performers.joinToString("\n") { if (it.instrument.isBlank()) it.name else "${it.name} - ${it.instrument}" },
      tuneId = t.standardId,
    )
  }
}

/** "2:00", "1:02:03" or "120" as seconds. */
fun parseClock(text: String): Int? {
  val parts = text.trim().split(':')
  if (parts.isEmpty() || parts.size > 3 || parts.any { it.isEmpty() || it.any { c -> !c.isDigit() } }) return null
  val numbers = parts.map { it.toInt() }
  if (numbers.drop(1).any { it >= 60 }) return null
  return numbers.fold(0) { acc, n -> acc * 60 + n }
}

/** Seconds as "m:ss", or "h:mm:ss" past an hour. */
fun clock(seconds: Int): String {
  val h = seconds / 3600
  val m = seconds % 3600 / 60
  val s = seconds % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Starting, finishing, grading and editing the user's transcriptions. */
class TranscriptionService(
  private val db: WoodshedDatabase,
  private val now: () -> Long = System::currentTimeMillis,
  private val scheduler: Scheduler = Sm2Scheduler,
) {
  private val dao get() = db.transcriptions()

  suspend fun wishlist(id: Long) = setStatus(id, TranscriptionStatus.WISHLIST)

  /** Starts working on a solo, due now so it comes up in the next session. */
  suspend fun start(id: Long) = db.withTransaction {
    val current = dao.state(id) ?: TranscriptionStateEntity(id, TranscriptionStatus.ACTIVE)
    dao.upsertState(current.copy(status = TranscriptionStatus.ACTIVE, startedAt = current.startedAt ?: now(), dueAt = current.dueAt ?: now()))
  }

  suspend fun finish(id: Long) = db.withTransaction {
    val current = dao.state(id) ?: TranscriptionStateEntity(id, TranscriptionStatus.DONE)
    dao.upsertState(current.copy(status = TranscriptionStatus.DONE, doneAt = now(), dueAt = null))
  }

  /** Back to an untouched solo: progress goes, review history stays. */
  suspend fun clear(id: Long) = dao.deleteState(id)

  suspend fun setNotes(id: Long, notes: String) = db.withTransaction {
    val current = dao.state(id) ?: TranscriptionStateEntity(id, TranscriptionStatus.WISHLIST)
    dao.upsertState(current.copy(notes = notes.ifBlank { null }))
  }

  /** Grades a practice pass on an active solo, scheduling the next one as for tunes. */
  suspend fun grade(id: Long, grade: Grade, elapsedMs: Long = 0) = db.withTransaction {
    val t = now()
    val before = dao.state(id) ?: return@withTransaction
    val after = scheduler.next(before.cardState, grade, t)
    db.reviewEvents().insert(
      ReviewEventEntity(
        itemKind = ReviewEventEntity.TRANSCRIPTION, itemId = id, exerciseId = null, transposition = "C", keyWrittenPitchClass = null,
        keyWrittenMinor = null, grade = grade, reviewedAt = t, elapsedMs = elapsedMs, schedulerId = scheduler.id, schedulerVersion = scheduler.version,
        intervalBefore = before.intervalDays, intervalAfter = after.intervalDays, easeBefore = before.ease, easeAfter = after.ease,
      )
    )
    dao.upsertState(before.withCardState(after).copy(lastReviewedAt = t))
  }

  /** What each grade would schedule, for labelling the buttons. */
  suspend fun preview(id: Long): Map<Grade, Long?> {
    val state = dao.state(id) ?: return emptyMap()
    return Grade.entries.associateWith { scheduler.next(state.cardState, it, now()).dueAt }
  }

  /** Adds one of the user's own solos and returns its id. */
  suspend fun add(draft: TranscriptionDraft): Long = db.withTransaction {
    require(draft.problems().isEmpty())
    val base = SeedValidator.USER_SLUG_PREFIX + TuneLibrary.slugify("${draft.title} ${draft.soloist}").ifEmpty { "solo" }
    var slug = base
    var n = 2
    while (dao.bySlug(slug) != null) slug = "$base-${n++}"
    dao.insert(entity(draft, slug))
  }

  /** Edits one of the user's own solos. Seeded solos are not editable; reporting a problem fixes them for everyone. */
  suspend fun edit(id: Long, draft: TranscriptionDraft) = db.withTransaction {
    require(draft.problems().isEmpty())
    val current = dao.byId(id)?.takeIf { it.userCreated } ?: return@withTransaction
    dao.update(listOf(entity(draft, current.slug).copy(id = id)))
  }

  suspend fun delete(id: Long) = db.withTransaction {
    val t = dao.byId(id)?.takeIf { it.userCreated } ?: return@withTransaction
    dao.deleteState(id)
    dao.update(listOf(t.copy(archivedAt = now())))
  }

  private suspend fun setStatus(id: Long, status: TranscriptionStatus) = db.withTransaction {
    val current = dao.state(id) ?: TranscriptionStateEntity(id, status)
    dao.upsertState(current.copy(status = status))
  }

  private fun entity(d: TranscriptionDraft, slug: String) = TranscriptionEntity(
    slug = slug,
    standardId = d.tuneId,
    title = d.title.trim(),
    soloist = d.soloist.trim(),
    instrument = d.instrument.trim(),
    context = d.context.trim().ifEmpty { null },
    url = d.url.trim(),
    startSeconds = parseClock(d.start),
    endSeconds = parseClock(d.end),
    keyName = d.keyName,
    keyPitchClass = Key.pitchClassOf(d.keyName),
    tonality = d.tonality,
    performers = d.performerList(),
    userCreated = true,
  )

  companion object {
    /** Playback starts and ends this many seconds outside the solo, so its first and last notes are not clipped. */
    const val PADDING_SECONDS = 5
  }
}
