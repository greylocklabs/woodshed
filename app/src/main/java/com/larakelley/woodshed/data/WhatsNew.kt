package com.larakelley.woodshed.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.larakelley.woodshed.domain.ChangelogRelease
import com.larakelley.woodshed.domain.parseChangelog
import com.larakelley.woodshed.domain.releasesSince

/** Decides when the What's new dialog shows: once after each update, never on a fresh install. */
class WhatsNew(private val prefs: SharedPreferences, private val readChangelog: () -> String) {
  /** The releases to show for [current], or none. No version seen yet means a fresh install. */
  fun pending(current: String): List<ChangelogRelease> {
    val seen = prefs.getString(SEEN, null)
    if (seen == current) return emptyList()
    if (seen == null) {
      markSeen(current)
      return emptyList()
    }
    val releases = runCatching { releasesSince(parseChangelog(readChangelog()), seen, current) }.getOrDefault(emptyList())
    if (releases.isEmpty()) markSeen(current)
    return releases
  }

  fun markSeen(current: String) = prefs.edit { putString(SEEN, current) }

  companion object {
    const val SEEN = "whats_new_seen"
  }
}
