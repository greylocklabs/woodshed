package com.larakelley.woodshed.data

import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.Instant
import java.time.ZoneId

/**
 * Decides when to ask for a Play review: once the app has been installed a week and used on a few days, and not
 * again for a few months. Play applies its own limits on top and never says whether it showed the prompt.
 */
class ReviewPrompt(
  private val prefs: SharedPreferences,
  private val installedAt: Long,
  private val now: () -> Long = System::currentTimeMillis,
  private val zone: ZoneId = ZoneId.systemDefault(),
) {
  /** Whether to ask now, given the times practice was logged. */
  fun shouldAsk(practiceTimes: List<Long>): Boolean {
    val at = now()
    if (at - installedAt < MIN_INSTALLED_MS) return false
    val lastAsked = prefs.getLong(ASKED_AT, 0L)
    if (lastAsked > 0 && at - lastAsked < BETWEEN_ASKS_MS) return false
    return practiceTimes.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.distinct().size >= MIN_PRACTICE_DAYS
  }

  fun asked() = prefs.edit { putLong(ASKED_AT, now()) }

  companion object {
    const val ASKED_AT = "review_asked_at"
    const val MIN_PRACTICE_DAYS = 3
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    const val MIN_INSTALLED_MS = 7 * DAY_MS
    const val BETWEEN_ASKS_MS = 90 * DAY_MS
  }
}
