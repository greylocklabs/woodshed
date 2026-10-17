package com.larakelley.woodshed.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
 * A problem report: sent to the report service, which files it as a GitHub issue, or opened as a prefilled
 * GitHub new-issue page. Either way it carries only the item, its current details, the user's note and the app
 * and Android versions, and nothing leaves the phone until the user sends it.
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

  /** The report as the report service takes it; worker/src/report.ts renders the same issue as [body]. */
  fun json(appVersion: String, androidVersion: String): String = Json.encodeToString(
    ReportPayload(subject.name, problem, item, id, details.map { listOf(it.first, it.second) }, note.ifBlank { null }, appVersion, androidVersion),
  )

  /** GitHub's new-issue page with the title, body and a label filled in. */
  fun issueUrl(appVersion: String, androidVersion: String, repo: String = REPO): String {
    val label = if (subject == ReportSubject.APP && problem == "An idea") "enhancement" else "bug"
    return "https://github.com/$repo/issues/new?title=${enc(title)}&body=${enc(body(appVersion, androidVersion))}&labels=${enc(label)}"
  }

  companion object {
    const val REPO = "greylocklabs/woodshed"

    /** The report service, a Cloudflare Worker in worker/ that files reports as the woodshed-reports GitHub app. */
    const val SERVICE = "https://woodshed-reports.lararosekelley.workers.dev/report"

    /** Notes ride in the issue page's address, which browsers and GitHub cut off past several thousand characters. */
    const val MAX_NOTE = 1500

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
  }
}

@Serializable
private data class ReportPayload(
  val subject: String,
  val problem: String,
  val item: String?,
  val id: String?,
  val details: List<List<String>>,
  val note: String?,
  val appVersion: String,
  val androidVersion: String,
)
