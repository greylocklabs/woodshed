package com.larakelley.woodshed.ui.history

import android.app.Application
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.larakelley.woodshed.data.PastBlock
import com.larakelley.woodshed.data.PastSession
import com.larakelley.woodshed.data.PracticeDay
import com.larakelley.woodshed.data.PracticeStats
import com.larakelley.woodshed.data.db.BlockOutcome
import com.larakelley.woodshed.domain.Grade
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HistoryScreenTest {
  @get:Rule val compose = createComposeRule()

  private val oct15 = LocalDate.of(2026, 10, 15)
  private val session = PastSession(
    id = 1,
    startedAt = oct15.atTime(19, 40).toInstant(ZoneOffset.UTC).toEpochMilli(),
    endedAt = oct15.atTime(20, 10).toInstant(ZoneOffset.UTC).toEpochMilli(),
    plannedMinutes = 30,
    completed = false,
    blocks = listOf(
      PastBlock("Long tones", "Tone", 5, BlockOutcome.DONE, null, 300_000),
      PastBlock("Solar", "Guide-tone solo", 25, BlockOutcome.GRADED, Grade.GOOD, 1_380_000),
      PastBlock("Blue Bossa", "Melody from memory", 10, null, null, 0),
    ),
  )

  @Test
  fun aSessionSummarisesAndOpensToItsBlocks() {
    compose.setContent { HistoryScreen(HistoryUi(sessions = listOf(session), loaded = true), HistoryActions(), zone = ZoneOffset.UTC) }
    compose.onNodeWithText("Thu, Oct 15, 7:40 PM").assertExists()
    compose.onNodeWithText("28 min of 30 min, 2 of 3 blocks, ended early").assertExists()
    compose.onNodeWithText("Graded good").assertDoesNotExist()
    compose.onNodeWithTag("session-1").performClick()
    compose.onNodeWithText("Graded good").assertExists()
    compose.onNodeWithText("Not reached").assertExists()
    compose.onNodeWithText("10 min planned").assertExists()
  }

  @Test
  fun aSessionIsDeletedOnlyOnceConfirmed() {
    val deleted = mutableListOf<Long>()
    compose.setContent {
      HistoryScreen(HistoryUi(sessions = listOf(session), loaded = true), HistoryActions(delete = { deleted += it }), zone = ZoneOffset.UTC)
    }
    compose.onNodeWithTag("delete-session-1").assertDoesNotExist()
    compose.onNodeWithTag("session-1").performClick()
    compose.onNodeWithTag("delete-session-1").performClick()
    compose.onNodeWithText("Cancel").performClick()
    assertEquals(emptyList<Long>(), deleted)
    compose.onNodeWithTag("delete-session-1").performClick()
    compose.onNodeWithTag("confirm-delete-session").performClick()
    assertEquals(listOf(1L), deleted)
  }

  @Test
  fun theCalendarMarksPracticeDaysAndListsADaysSessions() {
    val picked = mutableListOf<LocalDate>()
    val months = mutableListOf<YearMonth>()
    val ui = HistoryUi(
      sessions = listOf(session),
      days = mapOf(oct15 to PracticeDay(oct15, 1_680_000, 2, 1)),
      month = YearMonth.of(2026, 10),
      selected = oct15,
      loaded = true,
    )
    compose.setContent {
      HistoryScreen(
        ui,
        HistoryActions(showMonth = {
          months += it
        }, select = { picked += it }, sessionsOn = { listOf(session) }),
        zone = ZoneOffset.UTC,
      )
    }
    compose.onNodeWithTag("history-calendar").performClick()
    compose.onNodeWithTag("month").assertTextEquals("October 2026")
    compose.onNodeWithTag("day-summary").assertTextEquals("1 session, 28 min, 2 reviews")
    compose.onNodeWithTag("calendar").performScrollToNode(hasTestTag("session-1"))
    compose.onNodeWithTag("session-1").assertExists()
    compose.onNodeWithTag("calendar").performScrollToNode(hasTestTag("day-2026-10-03"))
    compose.onNodeWithTag("day-2026-10-03").performClick()
    compose.onNodeWithTag("calendar").performScrollToNode(hasTestTag("month-next"))
    compose.onNodeWithTag("month-next").performClick()
    assertEquals(listOf(LocalDate.of(2026, 10, 3)), picked)
    assertEquals(listOf(YearMonth.of(2026, 11)), months)
  }

  @Test
  fun statsShowTotalsStreaksAndKeys() {
    val stats = PracticeStats(
      practicedMs = 125 * 60_000L, sessions = 6, currentStreak = 1, longestStreak = 4,
      topTunes = listOf("Solar" to 7, "Nardis" to 1), repsByKey = List(12) { if (it == 0) 5 else 0 }.toMutableList().also { it[5] = 3 },
      learning = 3, repertoire = 2, reachedRepertoireThisMonth = 1,
    )
    compose.setContent {
      HistoryScreen(HistoryUi(sessions = listOf(session), stats = stats, loaded = true), HistoryActions(), initialView = HistoryView.STATS)
    }
    compose.onNodeWithText("2 h 5 min").assertExists()
    compose.onNodeWithText("1 day").assertExists()
    compose.onNodeWithText("4 days").assertExists()
    compose.onNodeWithTag("stat-tunes").assertTextEquals("3 learning, 2 in repertoire. 1 reached repertoire this month.")
    compose.onNodeWithText("7 reps").assertExists()
    compose.onNodeWithText("1 rep").assertExists()
    compose.onNodeWithTag("stats").performScrollToNode(hasTestTag("key-bars"))
    compose.onNodeWithTag("key-bars").assertExists()
  }

  @Test
  fun nothingYetSaysWhereHistoryComesFrom() {
    compose.setContent { HistoryScreen(HistoryUi(loaded = true), HistoryActions()) }
    compose.onNodeWithTag("empty-state").assertExists()
  }

  @Test
  fun aDayWithASessionNeverSaysNoPractice() {
    val day = LocalDate.of(2026, 10, 16)
    assertEquals("1 session, no blocks finished", dayLabel(PracticeDay(day, 0, 0, 1)))
    assertEquals("2 sessions, under a minute, 1 review", dayLabel(PracticeDay(day, 20_000, 1, 2)))
    assertEquals("3 reviews", dayLabel(PracticeDay(day, 0, 3, 0)))
    assertEquals("No practice", dayLabel(null))
  }

  @Test
  fun minutesRoundAndReadInHours() {
    assertEquals("28 min", minutesLabel(28 * 60_000L + 20_000))
    assertEquals("1 h", minutesLabel(60 * 60_000L))
    assertEquals("1 h 5 min", minutesLabel(65 * 60_000L))
  }

  @Test
  fun weeksStartOnMondayAndPadTheMonth() {
    // October 2026 starts on a Thursday and has 31 days.
    val weeks = monthWeeks(YearMonth.of(2026, 10))
    assertEquals(5, weeks.size)
    assertEquals(listOf(null, null, null, LocalDate.of(2026, 10, 1)), weeks.first().take(4))
    assertEquals(LocalDate.of(2026, 10, 31), weeks.last()[5])
    assertEquals(null, weeks.last()[6])
  }
}
