package com.larakelley.woodshed.domain

import java.net.URLEncoder

/** What a report is about, and the problems people most often find with that kind of thing. */
enum class ReportSubject(val label: String, val problems: List<String>) {
  TUNE(
    "Tune",
    listOf(
      "Wrong key",
      "Wrong major or minor",
      "Wrong composer or year",
      "Wrong form, bars or style",
      "Broken or wrong link",
      "Missing details",
      "Duplicate tune",
      "Something else",
    ),
  ),
  SOLO("Solo", listOf("Wrong solo times", "Wrong key", "Wrong performers or details", "Broken or wrong link", "Something else")),
  EXERCISE("Exercise", listOf("Wrong instruments", "Unclear instructions", "Wrong tempo or time", "Something else")),
  APP("App", listOf("Something is broken", "Something looks wrong", "An idea", "Something else")),
}

/**
 * A problem report, sent as a prefilled GitHub new-issue page. Nothing leaves the phone until the user submits
 * it on GitHub, and it carries only the item, its current details, the user's note and the app and Android versions.
 */
data class Report(
  val subject: ReportSubject,
  val problem: String,
  /** The item's name, such as a tune title; null for the app itself. */
  val item: String? = null,
  /** Its stable id, such as a tune slug, so the report can be matched to the data. */
  val id: String? = null,
  /** The values the user is reporting on, as shown in the app. */
  val details: List<Pair<String, String>> = emptyList(),
  val note: String = "",
) {
  val title: String
    get() = listOfNotNull(problem, item?.let { "in \"$it\"" }).joinToString(" ")

  fun body(appVersion: String, androidVersion: String): String = buildString {
    appendLine("**${subject.label}:** ${item ?: "Woodshed"}${id?.let { " (`$it`)" } ?: ""}")
    appendLine("**Problem:** $problem")
    if (details.isNotEmpty()) {
      appendLine()
      appendLine("What the app shows:")
      details.forEach { (k, v) -> appendLine("- $k: $v") }
    }
    appendLine()
    appendLine("**What it should be, or what happened:**")
    appendLine(note.ifBlank { "(please describe)" })
    appendLine()
    append("Woodshed $appVersion, Android $androidVersion")
  }

  /** GitHub's new-issue page with the title, body and a label filled in. */
  fun issueUrl(appVersion: String, androidVersion: String, repo: String = REPO): String {
    val label = if (subject == ReportSubject.APP && problem == "An idea") "enhancement" else "bug"
    return "https://github.com/$repo/issues/new?title=${enc(title)}&body=${enc(body(appVersion, androidVersion))}&labels=${enc(label)}"
  }

  companion object {
    const val REPO = "lararosekelley/woodshed"

    /** Notes ride in the issue page's address, which browsers and GitHub cut off past several thousand characters. */
    const val MAX_NOTE = 1500

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
  }
}
