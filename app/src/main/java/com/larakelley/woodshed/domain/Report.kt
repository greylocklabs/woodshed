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

  /** The issue title: [title], with a long item name cut short to fit the service's limit. */
  val summary: String
    get() {
      if (title.length <= MAX_SUMMARY || item == null) return title.take(MAX_SUMMARY)
      val room = (MAX_SUMMARY - "$problem in \"...\"".length).coerceAtLeast(0)
      return "$problem in \"${item.take(room).trimEnd()}...\"".take(MAX_SUMMARY)
    }

  val isIdea: Boolean
    get() = subject == ReportSubject.APP && problem == "An idea"

  /** [details] with what the report is about first. */
  private val allDetails: List<Pair<String, String>>
    get() = listOfNotNull(item?.let { subject.label to it }, id?.let { "Id" to it }) + details

  /** The issue body for the GitHub fallback: what greylocklabs/reports' issueFor renders from [json], less its "Sent from the app" line. */
  fun body(appVersion: String, androidVersion: String): String = buildString {
    appendLine(if (isIdea) "**The idea:**" else "**What happened:**")
    appendLine(note.trim().ifEmpty { "(please describe)" })
    if (allDetails.isNotEmpty()) {
      appendLine()
      appendLine("What the app shows:")
      allDetails.forEach { (k, v) -> appendLine("- $k: $v") }
    }
    appendLine()
    append("Woodshed $appVersion, Android $androidVersion")
  }

  /** The report in the shared report service's format. */
  fun json(appVersion: String, androidVersion: String): String = Json.encodeToString(
    ReportPayload(
      app = "woodshed",
      kind = if (isIdea) "idea" else "bug",
      summary = summary,
      note = note.trim().ifEmpty { null },
      details = allDetails.map { listOf(it.first, it.second) },
      appVersion = appVersion,
      platform = "Android $androidVersion",
    ),
  )

  /** GitHub's new-issue page with the title, body and a label filled in. */
  fun issueUrl(appVersion: String, androidVersion: String, repo: String = REPO): String {
    val label = if (isIdea) "enhancement" else "bug"
    return "https://github.com/$repo/issues/new?title=${enc(summary)}&body=${enc(body(appVersion, androidVersion))}&labels=${enc(label)}"
  }

  companion object {
    const val REPO = "greylocklabs/woodshed"

    /** The shared report service in greylocklabs/reports, which files reports as the greylocklabs-reports GitHub App. */
    const val SERVICE = "https://greylocklabs-reports.lararosekelley.workers.dev/report"

    /** Notes ride in the issue page's address, which browsers and GitHub cut off past several thousand characters. */
    const val MAX_NOTE = 1500

    /** The longest summary the service takes. */
    const val MAX_SUMMARY = 100

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
  }
}

@Serializable
private data class ReportPayload(
  val app: String,
  val kind: String,
  val summary: String,
  val note: String?,
  val details: List<List<String>>,
  val appVersion: String,
  val platform: String,
)
