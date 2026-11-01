package com.larakelley.woodshed.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTest {
  private val zone = ZoneId.of("America/New_York")

  // Sunday, 2026-11-01.
  private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone)

  private val sixPm = ReminderSchedule(enabled = true, minuteOfDay = 18 * 60)

  @Test
  fun comesLaterTodayOrOnTheNextDay() {
    assertEquals(at(1, 18), nextReminder(sixPm, at(1, 9)))
    assertEquals(at(2, 18), nextReminder(sixPm, at(1, 18)))
    assertEquals(at(2, 18), nextReminder(sixPm, at(1, 23, 30)))
  }

  @Test
  fun skipsDaysNotChosen() {
    val weekdays = sixPm.copy(days = DayOfWeek.entries.toSet() - DayOfWeek.SATURDAY - DayOfWeek.SUNDAY)
    // Saturday evening waits for Monday.
    assertEquals(at(2, 18), nextReminder(weekdays, at(1, 9).minusDays(1).withHour(20)))
    val sundays = sixPm.copy(days = setOf(DayOfWeek.SUNDAY))
    // Just past Sunday's time, the next is a week on.
    assertEquals(at(8, 18), nextReminder(sundays, at(1, 18, 1)))
  }

  @Test
  fun noneWhenOffOrWithNoDays() {
    assertNull(nextReminder(sixPm.copy(enabled = false), at(1, 9)))
    assertNull(nextReminder(sixPm.copy(days = emptySet()), at(1, 9)))
  }
}
