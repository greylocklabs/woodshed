package com.larakelley.woodshed.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larakelley.woodshed.data.HistoryService
import com.larakelley.woodshed.data.PastSession
import com.larakelley.woodshed.data.PracticeDay
import com.larakelley.woodshed.data.PracticeStats
import com.larakelley.woodshed.data.db.BlockOutcome
import com.larakelley.woodshed.theme.BackButton
import com.larakelley.woodshed.theme.EmptyState
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.theme.PillChoice
import com.larakelley.woodshed.theme.SectionLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class HistoryUi(
  val sessions: List<PastSession> = emptyList(),
  val days: Map<LocalDate, PracticeDay> = emptyMap(),
  val month: YearMonth = YearMonth.now(),
  val selected: LocalDate? = null,
  val stats: PracticeStats? = null,
  val loaded: Boolean = false,
)

class HistoryViewModel(
  private val history: HistoryService,
  private val zone: ZoneId = ZoneId.systemDefault(),
  private val today: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {
  private val _ui = MutableStateFlow(HistoryUi(month = YearMonth.from(today())))
  val ui: StateFlow<HistoryUi> = _ui.asStateFlow()

  fun refresh() {
    viewModelScope.launch {
      val sessions = history.sessions()
      val days = history.days(sessions, zone)
      val stats = history.stats(sessions, zone, today())
      _ui.update { it.copy(sessions = sessions, days = days, stats = stats, loaded = true) }
    }
  }

  fun showMonth(month: YearMonth) = _ui.update { it.copy(month = month, selected = null) }

  /** Selects a day to list its sessions; selecting it again clears the selection. */
  fun select(day: LocalDate) = _ui.update { it.copy(selected = if (it.selected == day) null else day) }

  /** The sessions started on [day]. */
  fun sessionsOn(day: LocalDate): List<PastSession> = _ui.value.sessions.filter { dateOf(it.startedAt) == day }

  private fun dateOf(at: Long) = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
}

/** What the history screen can do. */
data class HistoryActions(
  val showMonth: (YearMonth) -> Unit = {},
  val select: (LocalDate) -> Unit = {},
  val sessionsOn: (LocalDate) -> List<PastSession> = { emptyList() },
)

enum class HistoryView(val label: String) {
  LIST("List"),
  CALENDAR("Calendar"),
  STATS("Stats"),
}

@Composable
fun HistoryScreen(
  ui: HistoryUi,
  actions: HistoryActions,
  onBack: () -> Unit,
  zone: ZoneId = ZoneId.systemDefault(),
  initialView: HistoryView = HistoryView.LIST,
) {
  BackHandler(onBack = onBack)
  var view by rememberSaveable { mutableStateOf(initialView) }
  Column(Modifier.fillMaxSize()) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
      BackButton("Back", onBack, Modifier.testTag("close-history"))
      Text("History", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
    }
    PillChoice(HistoryView.entries.map { it.label to it }, view, { view = it }, Modifier.padding(horizontal = 12.dp, vertical = 12.dp), tagPrefix = "history")
    when {
      ui.loaded && ui.sessions.isEmpty() && ui.days.isEmpty() ->
        EmptyState("📜", "Nothing yet", "Sessions you run from the Practice tab, and tunes you grade, show up here.", scrolls = true)

      view == HistoryView.LIST -> SessionList(ui.sessions, zone)

      view == HistoryView.CALENDAR -> CalendarView(ui, actions, zone)

      else -> ui.stats?.let { StatsView(it) }
    }
  }
}

private val DATE = DateTimeFormatter.ofPattern("EEE, MMM d")
private val TIME = DateTimeFormatter.ofPattern("h:mm a")

/** "28 min", or "1 h 5 min" and "1 h" from an hour up. */
fun minutesLabel(ms: Long): String {
  val minutes = ((ms + 30_000) / 60_000).toInt()
  return when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
  }
}

/** The one-line summary of a session: time spent against planned, blocks finished, and how it ended. */
fun sessionSummary(s: PastSession): String {
  val finished = s.blocks.count { it.outcome != null }
  val ending = when {
    s.completed -> "finished"
    s.endedAt == null -> "not ended"
    else -> "ended early"
  }
  return "${minutesLabel(s.practicedMs)} of ${s.plannedMinutes} min, $finished of ${s.blocks.size} blocks, $ending"
}

private fun outcomeLabel(outcome: BlockOutcome?, grade: String?): String = when (outcome) {
  BlockOutcome.DONE -> "Done"
  BlockOutcome.SKIPPED -> "Skipped"
  BlockOutcome.GRADED -> "Graded ${grade.orEmpty()}".trim()
  null -> "Not reached"
}

@Composable
private fun SessionList(sessions: List<PastSession>, zone: ZoneId) {
  LazyColumn(Modifier.fillMaxSize().testTag("session-list")) {
    items(sessions, key = { it.id }) { s -> SessionRow(s, zone) }
  }
}

@Composable
private fun SessionRow(s: PastSession, zone: ZoneId) {
  val callie = LocalCallie.current
  var open by rememberSaveable(s.id) { mutableStateOf(false) }
  val started = Instant.ofEpochMilli(s.startedAt).atZone(zone)
  Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 12.dp).testTag("session-${s.id}")) {
    Text("${started.format(DATE)}, ${started.format(TIME)}", style = MaterialTheme.typography.titleMedium)
    Text(sessionSummary(s), style = MaterialTheme.typography.bodyMedium, color = callie.textMuted)
    if (open) {
      s.blocks.forEach { b ->
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(b.title, style = MaterialTheme.typography.bodyLarge)
            if (b.detail.isNotEmpty()) Text(b.detail, style = MaterialTheme.typography.bodySmall, color = callie.textMuted)
          }
          Column(horizontalAlignment = Alignment.End) {
            Text(outcomeLabel(b.outcome, b.grade?.name?.lowercase()), style = MaterialTheme.typography.labelLarge)
            Text(
              if (b.outcome ==
                null
              ) {
                "${b.plannedMinutes} min planned"
              } else {
                minutesLabel(b.elapsedMs)
              },
              style = MaterialTheme.typography.bodySmall,
              color = callie.textMuted,
            )
          }
        }
      }
    }
  }
  HorizontalDivider()
}

@Composable
private fun CalendarView(ui: HistoryUi, actions: HistoryActions, zone: ZoneId) {
  val callie = LocalCallie.current
  val month = ui.month
  LazyColumn(Modifier.fillMaxSize().testTag("calendar")) {
    item {
      Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { actions.showMonth(month.minusMonths(1)) }, modifier = Modifier.testTag("month-previous")) { Text("‹ Previous") }
        Text(
          "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f).testTag("month"),
          textAlign = TextAlign.Center,
        )
        TextButton(onClick = { actions.showMonth(month.plusMonths(1)) }, modifier = Modifier.testTag("month-next")) { Text("Next ›") }
      }
      Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
          DayOfWeek.entries.forEach { d ->
            Text(
              d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
              style = MaterialTheme.typography.labelMedium,
              color = callie.textMuted,
              modifier = Modifier.weight(1f),
              textAlign = TextAlign.Center,
            )
          }
        }
        monthWeeks(month).forEach { week ->
          Row {
            week.forEach { day ->
              Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                if (day != null) DayCell(day, ui.days[day], selected = day == ui.selected, onClick = { actions.select(day) })
              }
            }
          }
        }
      }
      ui.selected?.let { day ->
        val practice = ui.days[day]
        Text(
          day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        Text(
          dayLabel(practice),
          style = MaterialTheme.typography.bodyMedium,
          color = callie.textMuted,
          modifier = Modifier.padding(horizontal = 16.dp).testTag("day-summary"),
        )
      }
    }
    ui.selected?.let { day -> items(actions.sessionsOn(day), key = { it.id }) { s -> SessionRow(s, zone) } }
  }
}

/** What a day's practice adds up to, for its summary and its cell's description. */
fun dayLabel(day: PracticeDay?): String {
  if (day == null || (day.practicedMs == 0L && day.reviews == 0)) return "No practice"
  val parts = listOfNotNull(
    day.practicedMs.takeIf { it > 0 }?.let { "${minutesLabel(it)} in sessions" },
    day.reviews.takeIf { it > 0 }?.let { if (it == 1) "1 review" else "$it reviews" },
  )
  return parts.joinToString(", ")
}

/** The month as weeks from Monday, with nulls before the first day and after the last. */
fun monthWeeks(month: YearMonth): List<List<LocalDate?>> {
  val lead = month.atDay(1).dayOfWeek.value - 1
  val days = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
  return days.chunked(7).map { it + List(7 - it.size) { null } }
}

@Composable
private fun DayCell(day: LocalDate, practice: PracticeDay?, selected: Boolean, onClick: () -> Unit) {
  val callie = LocalCallie.current
  // A full hour in sessions fills the circle; reviews alone show as a light one.
  val strength = practice?.let { (it.practicedMs / 3_600_000f).coerceIn(0f, 1f).let { s -> if (s == 0f && it.reviews > 0) 0.25f else 0.25f + 0.75f * s } }
  Box(
    Modifier.size(40.dp).clip(CircleShape)
      .then(if (strength != null) Modifier.background(callie.accent.copy(alpha = strength), CircleShape) else Modifier)
      .then(if (selected) Modifier.border(2.dp, callie.text, CircleShape) else Modifier)
      .clickable(onClick = onClick)
      .semantics { contentDescription = "${day.format(DateTimeFormatter.ofPattern("MMMM d"))}: ${dayLabel(practice)}" }
      .testTag("day-$day"),
    contentAlignment = Alignment.Center,
  ) {
    Text("${day.dayOfMonth}", style = MaterialTheme.typography.bodyMedium, color = if (strength != null && strength > 0.6f) callie.accentText else callie.text)
  }
}

private val KEY_NAMES = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

@Composable
private fun StatsView(stats: PracticeStats) {
  val callie = LocalCallie.current
  LazyColumn(Modifier.fillMaxSize().testTag("stats"), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          StatTile("Time in sessions", minutesLabel(stats.practicedMs), Modifier.weight(1f), "stat-time")
          StatTile("Sessions", "${stats.sessions}", Modifier.weight(1f), "stat-sessions")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          StatTile("Current streak", daysLabel(stats.currentStreak), Modifier.weight(1f), "stat-streak")
          StatTile("Longest streak", daysLabel(stats.longestStreak), Modifier.weight(1f), "stat-longest")
        }
      }
      SectionLabel("Tunes")
      Text(
        "${stats.learning} learning, ${stats.repertoire} in repertoire. ${reachedLabel(stats.reachedRepertoireThisMonth)}",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.testTag("stat-tunes"),
      )
      if (stats.topTunes.isNotEmpty()) {
        SectionLabel("Most practised")
        stats.topTunes.forEach { (title, reps) ->
          Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(repsLabel(reps), style = MaterialTheme.typography.bodyLarge, color = callie.textMuted)
          }
        }
      }
      if (stats.repsByKey.any { it > 0 }) {
        SectionLabel("Graded reps by written key")
        KeyBars(stats.repsByKey)
      }
    }
  }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, tag: String) {
  val callie = LocalCallie.current
  Column(modifier.background(callie.surfaceAlt, RoundedCornerShape(16.dp)).padding(16.dp).testTag(tag)) {
    Text(value, style = MaterialTheme.typography.headlineSmall)
    Text(label, style = MaterialTheme.typography.bodyMedium, color = callie.textMuted)
  }
}

/** One thin bar per key from C, each named and counted in text so the bars are never read by color alone. */
@Composable
private fun KeyBars(reps: List<Int>) {
  val callie = LocalCallie.current
  val most = reps.max().coerceAtLeast(1)
  Column(Modifier.testTag("key-bars"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    reps.forEachIndexed { pc, n ->
      Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(KEY_NAMES[pc], style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(36.dp))
        Box(Modifier.weight(1f).height(10.dp)) {
          if (n > 0) {
            Box(
              Modifier.fillMaxWidth(n.toFloat() / most).height(10.dp)
                .background(callie.accent, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
            )
          }
        }
        Text("$n", style = MaterialTheme.typography.labelLarge, color = callie.textMuted, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
      }
    }
  }
}

fun daysLabel(n: Int) = if (n == 1) "1 day" else "$n days"

fun repsLabel(n: Int) = if (n == 1) "1 rep" else "$n reps"

fun reachedLabel(n: Int) = when (n) {
  0 -> "None reached repertoire this month."
  1 -> "1 reached repertoire this month."
  else -> "$n reached repertoire this month."
}
