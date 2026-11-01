package com.larakelley.woodshed.domain

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

/** When to remind the user to practice: one time of day, on the days they choose. */
data class ReminderSchedule(
  val enabled: Boolean = false,
  /** Minutes after midnight, local time. */
  val minuteOfDay: Int = DEFAULT_MINUTE,
  val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
) {
  val time: LocalTime get() = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)

  companion object {
    const val DEFAULT_MINUTE = 18 * 60
  }
}

/** The next reminder strictly after [after], or null when the reminder is off or has no days. */
fun nextReminder(schedule: ReminderSchedule, after: ZonedDateTime): ZonedDateTime? {
  if (!schedule.enabled || schedule.days.isEmpty()) return null
  // A week and a day covers every chosen day, including today's time if it has already passed.
  for (offset in 0..7L) {
    val day = after.toLocalDate().plusDays(offset)
    if (day.dayOfWeek !in schedule.days) continue
    val at = day.atTime(schedule.time).atZone(after.zone)
    if (at.isAfter(after)) return at
  }
  return null
}
